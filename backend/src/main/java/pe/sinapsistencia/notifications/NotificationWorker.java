package pe.sinapsistencia.notifications;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.sinapsistencia.notifications.domain.NotificationOutbox;
import pe.sinapsistencia.notifications.domain.NotificationOutboxStatus;
import pe.sinapsistencia.notifications.infrastructure.NotificationOutboxRepository;

/**
 * H-06: despacha filas {@code pending}/{@code processing}-con-lease-vencido del
 * outbox DESPUÉS de que la transacción de negocio que las encoló ya hizo commit.
 * Reclama con un {@code worker_token} + lease de 2 min por fila (UPDATE
 * condicional, ver {@link NotificationOutboxRepository#claim}) para que dos
 * procesadores nunca despachen el mismo trabajo. Tres intentos totales: el
 * primer fallo transitorio reintenta a los 30 s, el segundo a los 120 s: el
 * tercero (transitorio o no) deja la fila {@code failed}. Un error de
 * configuración (401/403) falla de inmediato, sin reintento.
 */
@Component
public class NotificationWorker {

	private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);

	private static final int BATCH_SIZE = 10;
	private static final int MAX_ATTEMPTS = 3;
	private static final Duration LEASE = Duration.ofMinutes(2);
	private static final Duration BACKOFF_FIRST = Duration.ofSeconds(30);
	private static final Duration BACKOFF_SECOND = Duration.ofSeconds(120);
	private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
	};

	private final NotificationOutboxRepository outboxRepository;
	private final ResendClient resendClient;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactionTemplate;
	private final boolean workerEnabled;

	public NotificationWorker(NotificationOutboxRepository outboxRepository,
			ResendClient resendClient,
			ObjectMapper objectMapper,
			PlatformTransactionManager transactionManager,
			@Value("${app.notifications.worker-enabled:true}") boolean workerEnabled) {
		this.outboxRepository = outboxRepository;
		this.resendClient = resendClient;
		this.objectMapper = objectMapper;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.workerEnabled = workerEnabled;
	}

	@Scheduled(fixedDelayString = "${app.notifications.worker-interval-ms:15000}")
	public void dispatchPending() {
		if (!workerEnabled) {
			return;
		}
		Instant now = Instant.now();
		List<UUID> candidates = transactionTemplate.execute(
				status -> outboxRepository.findClaimableIds(now, PageRequest.of(0, BATCH_SIZE)));
		if (candidates == null || candidates.isEmpty()) {
			return;
		}

		UUID token = UUID.randomUUID();
		Instant lockedUntil = Instant.now().plus(LEASE);
		for (UUID id : candidates) {
			Integer claimed = transactionTemplate.execute(
					status -> outboxRepository.claim(id, token, lockedUntil, Instant.now()));
			if (claimed != null && claimed == 1) {
				processOne(id, token);
			}
		}
	}

	private void processOne(UUID id, UUID token) {
		NotificationOutbox snapshot = transactionTemplate.execute(status -> outboxRepository.findById(id).orElse(null));
		if (snapshot == null || !token.equals(snapshot.getWorkerToken())) {
			return; // perdimos el lease entre el claim y este punto -- otro worker ya lo tiene
		}

		Map<String, Object> payload = objectMapper.convertValue(snapshot.getPayload(), PAYLOAD_TYPE);
		String idempotencyKey = "notification/" + snapshot.getId();

		try {
			ResendClient.SendResult result = resendClient.sendRaw(payload, idempotencyKey);
			if (result.providerMessageId() == null || result.providerMessageId().isBlank()) {
				// 2xx sin id: respuesta ambigua -- nunca se marca como entregado.
				handleFailure(id, token, result.httpStatus(), "ambiguous_response",
						"El proveedor respondió 2xx sin id de mensaje", false);
				return;
			}
			finalizeAccepted(id, token, result);
		} catch (HttpClientErrorException ex) {
			int httpStatus = ex.getStatusCode().value();
			boolean transientError = httpStatus == 429;
			handleFailure(id, token, httpStatus, "http_" + httpStatus, sanitize(ex.getMessage()), transientError);
		} catch (HttpServerErrorException ex) {
			int httpStatus = ex.getStatusCode().value();
			handleFailure(id, token, httpStatus, "http_" + httpStatus, sanitize(ex.getMessage()), true);
		} catch (ResourceAccessException ex) {
			handleFailure(id, token, null, "timeout", sanitize(ex.getMessage()), true);
		} catch (Exception ex) {
			log.error("[outbox] error inesperado despachando {}: {}", id, ex.getMessage());
			handleFailure(id, token, null, "unexpected_error", sanitize(ex.getMessage()), false);
		}
	}

	private void finalizeAccepted(UUID id, UUID token, ResendClient.SendResult result) {
		transactionTemplate.executeWithoutResult(status ->
				outboxRepository.findById(id)
						.filter(o -> token.equals(o.getWorkerToken()))
						.ifPresent(o -> {
							o.setStatus(NotificationOutboxStatus.ACCEPTED_BY_PROVIDER);
							o.setProviderMessageId(result.providerMessageId());
							o.setProviderHttpStatus(result.httpStatus());
							o.setAcceptedAt(Instant.now());
							o.setLockedUntil(null);
							outboxRepository.save(o);
							log.info("[outbox] {} aceptado por el proveedor (id={})", id, result.providerMessageId());
						}));
	}

	private void handleFailure(UUID id, UUID token, Integer httpStatus, String errorCode, String errorMessage,
			boolean transientError) {
		transactionTemplate.executeWithoutResult(status ->
				outboxRepository.findById(id)
						.filter(o -> token.equals(o.getWorkerToken()))
						.ifPresent(o -> {
							int attempts = o.getAttemptCount() + 1;
							o.setAttemptCount(attempts);
							o.setProviderHttpStatus(httpStatus);
							o.setLastErrorCode(errorCode);
							o.setLastErrorMessage(errorMessage);
							o.setRetryable(transientError);
							o.setLockedUntil(null);
							if (!transientError || attempts >= MAX_ATTEMPTS) {
								o.setStatus(NotificationOutboxStatus.FAILED);
								log.warn("[outbox] {} failed definitivo tras {} intento(s): {} {}", id, attempts,
										errorCode, errorMessage);
							} else {
								o.setStatus(NotificationOutboxStatus.PENDING);
								o.setNextAttemptAt(Instant.now().plus(attempts == 1 ? BACKOFF_FIRST : BACKOFF_SECOND));
								log.info("[outbox] {} reintentará (intento {} de {}): {}", id, attempts, MAX_ATTEMPTS,
										errorCode);
							}
							outboxRepository.save(o);
						}));
	}

	private static String sanitize(String message) {
		if (message == null) {
			return "Error desconocido";
		}
		String trimmed = message.strip();
		return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
	}
}
