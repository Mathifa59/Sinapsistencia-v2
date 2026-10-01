package pe.sinapsistencia.ml.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.annotation.PreDestroy;
import pe.sinapsistencia.shared.exception.ApiException;
import pe.sinapsistencia.shared.exception.ServiceUnavailableException;

import org.springframework.http.HttpStatus;

/**
 * Proxy al servicio ML (FastAPI) — el ML NO se reescribe; Spring solo lo
 * consume vía RestClient con timeout 5s y normaliza snake_case → camelCase,
 * igual que el BFF legacy.
 */
@Service
public class MlProxyService {

	private static final Logger log = LoggerFactory.getLogger(MlProxyService.class);

	/** Presupuesto total del chequeo de salud concurrente (H-01): /health + /api/v1/model/info. */
	private static final Duration HEALTH_TOTAL_BUDGET = Duration.ofSeconds(4);

	private final RestClient restClient;
	private final RestClient healthRestClient;
	private final ExecutorService healthExecutor;

	public MlProxyService(@Value("${app.ml.service-url}") String mlServiceUrl) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(5));
		factory.setReadTimeout(Duration.ofSeconds(5));
		this.restClient = RestClient.builder()
				.baseUrl(mlServiceUrl)
				.requestFactory(factory)
				.build();

		// H-01: cliente dedicado para /health y /api/v1/model/info con limite propio de
		// 3s -- antes se declaraba una fabrica igual pero las llamadas usaban `restClient`
		// (5s), asi que el limite anunciado en el comentario nunca se aplicaba.
		SimpleClientHttpRequestFactory healthFactory = new SimpleClientHttpRequestFactory();
		healthFactory.setConnectTimeout(Duration.ofSeconds(3));
		healthFactory.setReadTimeout(Duration.ofSeconds(3));
		this.healthRestClient = RestClient.builder()
				.baseUrl(mlServiceUrl)
				.requestFactory(healthFactory)
				.build();

		this.healthExecutor = Executors.newFixedThreadPool(2, runnable -> {
			Thread thread = new Thread(runnable, "ml-health-check");
			thread.setDaemon(true);
			return thread;
		});
	}

	@PreDestroy
	void shutdownHealthExecutor() {
		healthExecutor.shutdownNow();
	}

	/** POST /api/v1/risk-assessment — normaliza la respuesta a camelCase (contrato legacy). */
	public Map<String, Object> riskAssessment(Map<String, Object> body) {
		JsonNode data;
		try {
			data = restClient.post()
					.uri("/api/v1/risk-assessment")
					.header("Content-Type", "application/json")
					.body(body)
					.retrieve()
					.body(JsonNode.class);
		} catch (RestClientResponseException ex) {
			String detail = extractDetail(ex);
			throw new MlHttpException(ex.getStatusCode().value(), detail);
		} catch (Exception ex) {
			log.warn("ML service no disponible: {}", ex.getMessage());
			throw new ServiceUnavailableException(
					"El servicio de evaluación de riesgo no está disponible. Intenta más tarde.");
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("caseId", text(data, "case_id"));
		result.put("riskScore", number(data, "risk_score"));
		result.put("riskLevel", text(data, "risk_level"));

		List<Map<String, Object>> factors = new ArrayList<>();
		if (data.has("risk_factors") && data.get("risk_factors").isArray()) {
			for (JsonNode f : data.get("risk_factors")) {
				Map<String, Object> factor = new LinkedHashMap<>();
				factor.put("name", text(f, "name"));
				factor.put("weight", number(f, "weight"));
				factor.put("value", number(f, "value"));
				factor.put("contribution", number(f, "contribution"));
				factor.put("description", text(f, "description"));
				factors.add(factor);
			}
		}
		result.put("riskFactors", factors);

		List<String> recommendations = new ArrayList<>();
		if (data.has("recommendations") && data.get("recommendations").isArray()) {
			data.get("recommendations").forEach(r -> recommendations.add(r.asText()));
		}
		result.put("recommendations", recommendations);
		result.put("specialtyRiskBaseline", number(data, "specialty_risk_baseline"));
		result.put("modelVersion", text(data, "model_version"));
		return result;
	}

	/** POST /api/v1/recommendations — matching ML; lanza si el servicio no responde (el caller hace fallback). */
	public JsonNode recommendations(String doctorId, Map<String, Object> doctorProfile, int topK,
			java.util.List<Map<String, Object>> lawyers) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("doctor_id", doctorId);
		body.put("doctor_profile", doctorProfile);
		body.put("top_k", topK);
		// H-02: enviar el campo si lawyers != null, AUN VACIO -- omitirlo cuando la
		// lista esta vacia (0 abogados disponibles) hacia que el ML service lo viera
		// como ausente y activara el corpus estatico de perfiles ajenos a la BD.
		if (lawyers != null) {
			body.put("lawyers", lawyers);
		}
		return restClient.post()
				.uri("/api/v1/recommendations")
				.header("Content-Type", "application/json")
				.body(body)
				.retrieve()
				.body(JsonNode.class);
	}

	/**
	 * GET /health + /api/v1/model/info en paralelo, presupuesto total
	 * {@link #HEALTH_TOTAL_BUDGET}. Distingue (RF-01.3) conectividad del proceso
	 * (`status`) de disponibilidad del modelo (`modelReady`):
	 * <ul>
	 *   <li>El proceso no responde dentro del presupuesto → {@code offline}.</li>
	 *   <li>El proceso responde pero {@code /model/info} falla o el modelo no
	 *       está cargado → {@code online} con {@code modelReady=false} (UI:
	 *       "Degradado").</li>
	 *   <li>Ambos responden y el modelo está cargado → {@code online} con
	 *       {@code modelReady=true}.</li>
	 * </ul>
	 * {@code cancel(true)} no garantiza cerrar el socket subyacente -- solo
	 * interrumpe el hilo del executor acotado para no bloquearlo indefinidamente.
	 */
	public Map<String, Object> health() {
		long budgetMs = HEALTH_TOTAL_BUDGET.toMillis();
		long startNanos = System.nanoTime();

		Future<JsonNode> healthFuture = healthExecutor
				.submit(() -> healthRestClient.get().uri("/health").retrieve().body(JsonNode.class));
		Future<JsonNode> modelFuture = healthExecutor
				.submit(() -> healthRestClient.get().uri("/api/v1/model/info").retrieve().body(JsonNode.class));

		JsonNode health;
		try {
			health = healthFuture.get(budgetMs, TimeUnit.MILLISECONDS);
		} catch (Exception ex) {
			healthFuture.cancel(true);
			modelFuture.cancel(true);
			log.warn("ML /health no respondió dentro de {}: {}", HEALTH_TOTAL_BUDGET, ex.getMessage());
			return Map.of("status", "offline", "message", "El servicio ML no está disponible");
		}

		long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
		long remainingMs = Math.max(0, budgetMs - elapsedMs);
		JsonNode model = null;
		try {
			model = modelFuture.get(remainingMs, TimeUnit.MILLISECONDS);
		} catch (Exception ex) {
			modelFuture.cancel(true);
			log.warn("ML /api/v1/model/info no respondió: {}", ex.getMessage());
		}

		boolean modelReady = model != null && "loaded".equals(text(model, "status"));

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("status", "online");
		result.put("modelReady", modelReady);
		if (!modelReady) {
			result.put("message", model == null
					? "No se pudo confirmar el estado del modelo."
					: "El servicio ML respondió pero el modelo aún no está listo.");
		}
		result.put("service", text(health, "service"));
		result.put("version", text(health, "version"));
		if (model != null) {
			Map<String, Object> modelInfo = new LinkedHashMap<>();
			modelInfo.put("status", text(model, "status"));
			modelInfo.put("modelVersion", text(model, "model_version"));
			modelInfo.put("contentModel", text(model, "content_model"));
			modelInfo.put("collaborativeModel", text(model, "collaborative_model"));
			modelInfo.put("riskModel", text(model, "risk_model"));
			result.put("model", modelInfo);
		}
		return result;
	}

	private static String extractDetail(RestClientResponseException ex) {
		try {
			JsonNode error = new com.fasterxml.jackson.databind.ObjectMapper()
					.readTree(ex.getResponseBodyAsString());
			return error.hasNonNull("detail") ? error.get("detail").asText()
					: "Error en la evaluación de riesgo";
		} catch (Exception parseEx) {
			return "Error en la evaluación de riesgo";
		}
	}

	private static String text(JsonNode node, String field) {
		return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
	}

	private static Object number(JsonNode node, String field) {
		return node != null && node.hasNonNull(field) ? node.get(field).numberValue() : null;
	}

	/** Error HTTP del ML con su status original (espeja apiError(detail, mlResponse.status)). */
	public static class MlHttpException extends ApiException {
		public MlHttpException(int status, String message) {
			super(HttpStatus.valueOf(status), message);
		}
	}
}
