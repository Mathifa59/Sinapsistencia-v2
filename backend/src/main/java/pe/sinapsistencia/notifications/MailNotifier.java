package pe.sinapsistencia.notifications;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Dispara los correos transaccionales de la plataforma vía {@link ResendClient} — el
 * asunto y el HTML llegan listos desde acá (ver {@link MailTemplates}).
 *
 * <p>Semántica <em>fire-and-forget</em>: método {@code @Async}, nunca lanza. Si
 * {@code app.resend.api-key} no está configurada (dev local sin cuenta de Resend) el
 * envío se omite y la app sigue funcionando con normalidad.
 */
@Service
public class MailNotifier {

	private static final Logger log = LoggerFactory.getLogger(MailNotifier.class);

	private final ResendClient resendClient;
	private final String frontendUrl;

	public MailNotifier(
			ResendClient resendClient,
			@Value("${app.frontend.url:http://localhost:4200}") String frontendUrl) {
		this.resendClient = resendClient;
		this.frontendUrl = stripTrailingSlash(frontendUrl);
	}

	/** {@code true} si hay un API key de Resend configurado (permite decidir fallbacks). */
	public boolean isConfigured() {
		return resendClient.isConfigured();
	}

	// ── Disparadores públicos ──────────────────────────────────────────────────

	/** HU-04: correo con enlace + token para restablecer la contraseña. */
	@Async
	public void sendPasswordReset(String to, String name, String token) {
		String resetLink = frontendUrl + "/reset-password?email=" + enc(to) + "&token=" + enc(token);
		dispatch("password_reset", to,
				"Restablece tu contraseña — Sinapsistencia",
				MailTemplates.passwordReset(name, resetLink, token));
	}

	/** Correo de bienvenida tras un registro exitoso. */
	@Async
	public void sendWelcome(String to, String name, String roleLabel) {
		dispatch("welcome", to,
				"Tu cuenta en Sinapsistencia está lista",
				MailTemplates.welcome(name, roleLabel, frontendUrl + "/"));
	}

	/** Aviso al abogado de que recibió una nueva solicitud de contacto. */
	@Async
	public void sendContactRequestReceived(String toLawyerEmail, String lawyerName, String doctorName,
			String caseTitle, String message) {
		dispatch("contact_request_received", toLawyerEmail,
				"Nueva solicitud de contacto — Sinapsistencia",
				MailTemplates.contactRequestReceived(lawyerName, doctorName, caseTitle, message,
						frontendUrl + "/lawyer/requests"));
	}

	/** Aviso al médico de que su solicitud fue aceptada o rechazada. */
	@Async
	public void sendContactRequestAnswered(String toDoctorEmail, String doctorName, String lawyerName,
			String caseTitle, boolean accepted, String responseMessage) {
		String subject = accepted
				? "Tu solicitud de contacto fue aceptada — Sinapsistencia"
				: "Respuesta a tu solicitud de contacto — Sinapsistencia";
		dispatch("contact_request_answered", toDoctorEmail, subject,
				MailTemplates.contactRequestAnswered(doctorName, lawyerName, caseTitle, accepted,
						responseMessage, frontendUrl + "/doctor/cases"));
	}

	// ── Envío ──────────────────────────────────────────────────────────────────

	private void dispatch(String type, String to, String subject, String html) {
		if (!isConfigured()) {
			log.warn("[mail] RESEND_API_KEY no configurada — correo '{}' a {} omitido", type, to);
			return;
		}
		if (to == null || to.isBlank()) {
			log.warn("[mail] correo '{}' sin destinatario — omitido", type);
			return;
		}
		try {
			resendClient.send(to, subject, html);
			log.info("[mail] Correo '{}' enviado a {}", type, to);
		} catch (Exception ex) {
			log.error("[mail] Error al enviar correo '{}' a {}: {}", type, to, ex.getMessage());
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
