package pe.sinapsistencia.notifications;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Dispara los correos transaccionales de la plataforma a un webhook de n8n, que
 * arma nada — el asunto y el HTML llegan listos (ver {@link MailTemplates}) — y
 * los entrega vía Gmail.
 *
 * <p>Semántica <em>fire-and-forget</em> idéntica a {@code N8nNotifier}: método
 * {@code @Async}, timeout corto y nunca lanza. Si {@code app.n8n.webhook-url} no
 * está configurada (dev local sin n8n) el disparo se omite y la app sigue
 * funcionando con normalidad.
 *
 * <p>Todos los métodos reciben ya los valores primitivos (String/boolean) — nunca
 * entidades JPA — para poder ejecutarse en otro hilo sin tocar el contexto de
 * persistencia.
 */
@Service
public class MailNotifier {

	private static final Logger log = LoggerFactory.getLogger(MailNotifier.class);

	/** Path del webhook de n8n; se concatena a la base configurada. */
	private static final String MAIL_WEBHOOK_PATH = "/webhook/sinapsistencia-mail";

	private final String webhookBaseUrl;
	private final String frontendUrl;
	private final RestClient restClient;

	public MailNotifier(
			@Value("${app.n8n.webhook-url:}") String webhookBaseUrl,
			@Value("${app.frontend.url:http://localhost:4200}") String frontendUrl) {
		this.webhookBaseUrl = webhookBaseUrl == null ? "" : webhookBaseUrl.strip();
		this.frontendUrl = stripTrailingSlash(frontendUrl);
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(3));
		factory.setReadTimeout(Duration.ofSeconds(5));
		this.restClient = RestClient.builder().requestFactory(factory).build();
	}

	/** {@code true} si hay un webhook de n8n configurado (permite decidir fallbacks). */
	public boolean isConfigured() {
		return !webhookBaseUrl.isBlank();
	}

	// ── Disparadores públicos ──────────────────────────────────────────────────

	/** HU-04: correo con enlace + token para restablecer la contraseña. */
	@Async
	public void sendPasswordReset(String to, String name, String token) {
		String resetLink = frontendUrl + "/reset-password?email=" + enc(to) + "&token=" + enc(token);
		Map<String, Object> extra = new HashMap<>();
		extra.put("token", token);
		extra.put("resetLink", resetLink);
		dispatch("password_reset", to, name,
				"Restablece tu contraseña — Sinapsistencia",
				MailTemplates.passwordReset(name, resetLink, token),
				extra);
	}

	/** Correo de bienvenida tras un registro exitoso. */
	@Async
	public void sendWelcome(String to, String name, String roleLabel) {
		dispatch("welcome", to, name,
				"Tu cuenta en Sinapsistencia está lista",
				MailTemplates.welcome(name, roleLabel, frontendUrl + "/"),
				Map.of("role", roleLabel));
	}

	/** Aviso al abogado de que recibió una nueva solicitud de contacto. */
	@Async
	public void sendContactRequestReceived(String toLawyerEmail, String lawyerName, String doctorName,
			String caseTitle, String message) {
		Map<String, Object> extra = new HashMap<>();
		extra.put("doctorName", doctorName);
		extra.put("caseTitle", caseTitle == null ? "" : caseTitle);
		dispatch("contact_request_received", toLawyerEmail, lawyerName,
				"Nueva solicitud de contacto — Sinapsistencia",
				MailTemplates.contactRequestReceived(lawyerName, doctorName, caseTitle, message, frontendUrl + "/lawyer/requests"),
				extra);
	}

	/** Aviso al médico de que su solicitud fue aceptada o rechazada. */
	@Async
	public void sendContactRequestAnswered(String toDoctorEmail, String doctorName, String lawyerName,
			String caseTitle, boolean accepted, String responseMessage) {
		Map<String, Object> extra = new HashMap<>();
		extra.put("lawyerName", lawyerName);
		extra.put("accepted", accepted);
		extra.put("caseTitle", caseTitle == null ? "" : caseTitle);
		String subject = accepted
				? "Tu solicitud de contacto fue aceptada — Sinapsistencia"
				: "Respuesta a tu solicitud de contacto — Sinapsistencia";
		dispatch("contact_request_answered", toDoctorEmail, doctorName, subject,
				MailTemplates.contactRequestAnswered(doctorName, lawyerName, caseTitle, accepted,
						responseMessage, frontendUrl + "/doctor/cases"),
				extra);
	}

	// ── Envío ──────────────────────────────────────────────────────────────────

	private void dispatch(String type, String to, String name, String subject, String html,
			Map<String, Object> extra) {
		if (!isConfigured()) {
			log.warn("[n8n-mail] N8N_WEBHOOK_URL no configurada — correo '{}' a {} omitido", type, to);
			return;
		}
		if (to == null || to.isBlank()) {
			log.warn("[n8n-mail] correo '{}' sin destinatario — omitido", type);
			return;
		}

		Map<String, Object> payload = new HashMap<>();
		payload.put("type", type);
		payload.put("to", to);
		payload.put("name", name == null ? "" : name);
		payload.put("subject", subject);
		payload.put("html", html);
		payload.putAll(extra);

		try {
			restClient.post()
					.uri(webhookBaseUrl + MAIL_WEBHOOK_PATH)
					.header("Content-Type", "application/json")
					.body(payload)
					.retrieve()
					.toBodilessEntity();
			log.info("[n8n-mail] Correo '{}' disparado a {}", type, to);
		} catch (Exception ex) {
			log.error("[n8n-mail] Error al disparar correo '{}' a {}: {}", type, to, ex.getMessage());
		}
	}

	private static String enc(String raw) {
		return URLEncoder.encode(raw == null ? "" : raw, StandardCharsets.UTF_8);
	}

	private static String stripTrailingSlash(String url) {
		String value = url == null || url.isBlank() ? "http://localhost:4200" : url.strip();
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
