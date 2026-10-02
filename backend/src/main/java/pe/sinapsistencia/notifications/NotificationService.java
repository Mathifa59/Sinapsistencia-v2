package pe.sinapsistencia.notifications;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import pe.sinapsistencia.notifications.domain.NotificationOutbox;
import pe.sinapsistencia.notifications.domain.NotificationOutboxStatus;
import pe.sinapsistencia.notifications.infrastructure.NotificationOutboxRepository;
import pe.sinapsistencia.notifications.web.dto.NotificationOutboxDto;

/**
 * H-06: encola avisos en la transacción de negocio del llamador --
 * {@link NotificationWorker} los despacha DESPUÉS del commit. Reemplaza las
 * llamadas directas {@code @Async} que disparaban los tres eventos correctivos
 * (solicitud recibida/contestada, alerta de riesgo) -- {@code RiskAlertNotifier}
 * se eliminó; {@link MailNotifier} se conserva solo para bienvenida/recuperación
 * de contraseña, que no entran al outbox.
 */
@Service
public class NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	private final NotificationOutboxRepository outboxRepository;
	private final ResendClient resendClient;
	private final String fromAddress;
	private final String defaultReplyTo;
	private final ObjectMapper objectMapper;

	public NotificationService(NotificationOutboxRepository outboxRepository,
			ResendClient resendClient,
			@Value("${app.resend.from}") String fromAddress,
			@Value("${app.resend.reply-to:}") String defaultReplyTo,
			ObjectMapper objectMapper) {
		this.outboxRepository = outboxRepository;
		this.resendClient = resendClient;
		this.fromAddress = fromAddress;
		this.defaultReplyTo = defaultReplyTo == null ? "" : defaultReplyTo.strip();
		this.objectMapper = objectMapper;
	}

	/**
	 * Encola un aviso. {@code eventKey} es la clave de deduplicación estable del
	 * evento de negocio (no de este intento) -- si ya existe una fila para esa
	 * clave, esta llamada es un no-op (reintento idempotente del llamador, ej. un
	 * doble-submit del formulario). Sin destinatario o sin API key configurada,
	 * la fila nace {@code skipped}: nunca se encola para "enviar" algo que no va a
	 * intentarse de verdad. {@code replyTo} nulo/vacío cae al Reply-To por
	 * defecto de la plataforma ({@code app.resend.reply-to}), igual que antes.
	 */
	public void enqueue(String eventKey, String type, UUID caseId, String resourceType, UUID resourceId,
			String recipient, String replyTo, String subject, String html) {
		if (outboxRepository.findByEventKey(eventKey).isPresent()) {
			log.info("[outbox] evento '{}' ya encolado -- no se duplica", eventKey);
			return;
		}

		boolean hasRecipient = recipient != null && !recipient.isBlank();
		String effectiveRecipient = hasRecipient ? recipient.strip() : "";
		String effectiveReplyTo = replyTo != null && !replyTo.isBlank()
				? replyTo.strip()
				: (defaultReplyTo.isBlank() ? null : defaultReplyTo);

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("from", fromAddress);
		payload.put("to", List.of(hasRecipient ? effectiveRecipient : ""));
		payload.put("subject", subject);
		payload.put("html", html);
		if (effectiveReplyTo != null) {
			payload.put("reply_to", effectiveReplyTo);
		}

		NotificationOutbox outbox = new NotificationOutbox(eventKey, type, caseId, resourceType, resourceId,
				effectiveRecipient, effectiveReplyTo, subject, objectMapper.valueToTree(payload));

		if (!hasRecipient) {
			outbox.setStatus(NotificationOutboxStatus.SKIPPED);
			outbox.setLastErrorCode("no_recipient");
			outbox.setLastErrorMessage("Sin destinatario configurado para este aviso");
		} else if (!resendClient.isConfigured()) {
			outbox.setStatus(NotificationOutboxStatus.SKIPPED);
			outbox.setLastErrorCode("no_api_key");
			outbox.setLastErrorMessage("RESEND_API_KEY no configurada");
		}

		try {
			outboxRepository.save(outbox);
			log.info("[outbox] evento '{}' encolado (status={})", eventKey, outbox.getStatus().getValue());
		} catch (DataIntegrityViolationException ex) {
			// Colisión de event_key por una inserción concurrente del mismo evento: ya quedó encolado.
			log.info("[outbox] evento '{}' encolado concurrentemente -- no se duplica", eventKey);
		}
	}

	/** GET /api/legal-cases/{id}/notifications -- lectura pura, el llamador ya validó permisos del caso. */
	@Transactional(readOnly = true)
	public List<NotificationOutboxDto> listForCase(UUID caseId, boolean includeProviderDetail) {
		return outboxRepository.findByCaseIdOrderByCreatedAtDesc(caseId).stream()
				.map(o -> NotificationOutboxDto.from(o, includeProviderDetail))
				.toList();
	}
}
