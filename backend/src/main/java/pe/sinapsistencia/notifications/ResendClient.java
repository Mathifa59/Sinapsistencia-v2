package pe.sinapsistencia.notifications;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP de la API de Resend (https://resend.com/docs/api-reference/emails/send-email).
 * Punto único de envío de correo de la plataforma — lo usan {@link MailNotifier} (correos
 * transaccionales) y {@code RiskAlertNotifier} (alerta de riesgo alto/crítico a un admin).
 *
 * <p>{@code fromAddress} solo necesita vivir en un dominio verificado en Resend — no
 * requiere ser una bandeja real. Si alguien responde un correo, esa respuesta va a
 * {@code replyToAddress} (opcional; vacío = sin header Reply-To).
 *
 * <p>No atrapa excepciones: cada llamador decide cómo loguear el fallo y con qué
 * semántica de fallback, igual que antes con el webhook de n8n.
 */
@Service
public class ResendClient {

	private static final String RESEND_API_URL = "https://api.resend.com/emails";

	private final String apiKey;
	private final String fromAddress;
	private final String replyToAddress;
	private final RestClient restClient;

	public ResendClient(
			@Value("${app.resend.api-key:}") String apiKey,
			@Value("${app.resend.from}") String fromAddress,
			@Value("${app.resend.reply-to:}") String replyToAddress) {
		this.apiKey = apiKey == null ? "" : apiKey.strip();
		this.fromAddress = fromAddress;
		this.replyToAddress = replyToAddress == null ? "" : replyToAddress.strip();
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(3));
		factory.setReadTimeout(Duration.ofSeconds(5));
		this.restClient = RestClient.builder().requestFactory(factory).build();
	}

	/** {@code true} si hay un API key de Resend configurado (permite decidir fallbacks). */
	public boolean isConfigured() {
		return !apiKey.isBlank();
	}

	/** Envía un correo. Lanza si la API de Resend responde con error o no responde a tiempo. */
	public void send(String to, String subject, String html) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("from", fromAddress);
		payload.put("to", List.of(to));
		payload.put("subject", subject);
		payload.put("html", html);
		if (!replyToAddress.isBlank()) {
			payload.put("reply_to", replyToAddress);
		}
		restClient.post()
				.uri(RESEND_API_URL)
				.header("Authorization", "Bearer " + apiKey)
				.header("Content-Type", "application/json")
				.body(payload)
				.retrieve()
				.toBodilessEntity();
	}
}
