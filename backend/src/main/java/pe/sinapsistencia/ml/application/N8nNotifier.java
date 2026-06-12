package pe.sinapsistencia.ml.application;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Webhook de n8n (alertas de riesgo alto/crítico) — semántica fire-and-forget
 * idéntica al lib/n8n.ts legacy: timeout 3s, nunca lanza, si n8n cae la app
 * sigue funcionando.
 */
@Service
public class N8nNotifier {

	private static final Logger log = LoggerFactory.getLogger(N8nNotifier.class);

	private final String webhookBaseUrl;
	private final RestClient restClient;

	public N8nNotifier(@Value("${app.n8n.webhook-url:}") String webhookBaseUrl) {
		this.webhookBaseUrl = webhookBaseUrl;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(3));
		factory.setReadTimeout(Duration.ofSeconds(3));
		this.restClient = RestClient.builder().requestFactory(factory).build();
	}

	@Async
	public void triggerRiskAlert(Map<String, Object> payload) {
		if (webhookBaseUrl == null || webhookBaseUrl.isBlank()) {
			log.warn("[n8n] N8N_WEBHOOK_URL no configurada — webhook omitido");
			return;
		}
		try {
			restClient.post()
					.uri(webhookBaseUrl + "/webhook/risk-alert")
					.header("Content-Type", "application/json")
					.body(payload)
					.retrieve()
					.toBodilessEntity();
			log.info("[n8n] Alerta de riesgo disparada (riskLevel={})", payload.get("riskLevel"));
		} catch (Exception ex) {
			log.error("[n8n] Error al disparar webhook: {}", ex.getMessage());
		}
	}
}
