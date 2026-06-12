package pe.sinapsistencia.ml.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;

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

	private final RestClient restClient;

	public MlProxyService(@Value("${app.ml.service-url}") String mlServiceUrl) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(5));
		factory.setReadTimeout(Duration.ofSeconds(5));
		this.restClient = RestClient.builder()
				.baseUrl(mlServiceUrl)
				.requestFactory(factory)
				.build();
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
	public JsonNode recommendations(String doctorId, Map<String, Object> doctorProfile, int topK) {
		Map<String, Object> body = Map.of(
				"doctor_id", doctorId,
				"doctor_profile", doctorProfile,
				"top_k", topK);
		return restClient.post()
				.uri("/api/v1/recommendations")
				.header("Content-Type", "application/json")
				.body(body)
				.retrieve()
				.body(JsonNode.class);
	}

	/** GET /health + /api/v1/model/info en paralelo (timeout 3s); offline si algo falla. */
	public Map<String, Object> health() {
		SimpleClientHttpRequestFactory fastFactory = new SimpleClientHttpRequestFactory();
		fastFactory.setConnectTimeout(Duration.ofSeconds(3));
		fastFactory.setReadTimeout(Duration.ofSeconds(3));

		try {
			CompletableFuture<JsonNode> healthFuture = CompletableFuture
					.supplyAsync(() -> restClient.get().uri("/health").retrieve().body(JsonNode.class));
			CompletableFuture<JsonNode> modelFuture = CompletableFuture
					.supplyAsync(() -> restClient.get().uri("/api/v1/model/info").retrieve().body(JsonNode.class));

			JsonNode health = healthFuture.get();
			JsonNode model = modelFuture.get();

			Map<String, Object> modelInfo = new LinkedHashMap<>();
			modelInfo.put("status", text(model, "status"));
			modelInfo.put("modelVersion", text(model, "model_version"));
			modelInfo.put("contentModel", text(model, "content_model"));
			modelInfo.put("collaborativeModel", text(model, "collaborative_model"));
			modelInfo.put("riskModel", text(model, "risk_model"));

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("status", "online");
			result.put("service", text(health, "service"));
			result.put("version", text(health, "version"));
			result.put("model", modelInfo);
			return result;
		} catch (Exception ex) {
			return Map.of(
					"status", "offline",
					"message", "El servicio ML no está disponible");
		}
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
