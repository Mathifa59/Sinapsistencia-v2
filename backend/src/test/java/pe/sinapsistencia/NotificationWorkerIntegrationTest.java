package pe.sinapsistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpServerErrorException;

import com.fasterxml.jackson.databind.ObjectMapper;

import pe.sinapsistencia.notifications.NotificationWorker;
import pe.sinapsistencia.notifications.ResendClient;
import pe.sinapsistencia.notifications.domain.NotificationOutbox;
import pe.sinapsistencia.notifications.domain.NotificationOutboxStatus;
import pe.sinapsistencia.notifications.infrastructure.NotificationOutboxRepository;

/**
 * H-06: NotificationWorker con un {@link ResendClient} simulado (200/403/429/500/timeout)
 * -- verifica clasificación transitorio/permanente, backoff y el tope de 3 intentos
 * SIN depender del proveedor real ni del scheduler (worker-interval-ms largo en este
 * perfil de test; {@link NotificationWorker#dispatchPending()} se llama a mano).
 */
@SpringBootTest(properties = {
		"spring.docker.compose.enabled=false",
		"app.notifications.worker-interval-ms=600000"
})
@Import(TestcontainersConfiguration.class)
class NotificationWorkerIntegrationTest {

	@Autowired
	private NotificationWorker worker;

	@Autowired
	private NotificationOutboxRepository outboxRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ResendClient resendClient;

	private NotificationOutbox freshOutbox(String eventKey) {
		Map<String, Object> payload = Map.of(
				"from", "notificaciones@sinapsistencia.com",
				"to", java.util.List.of("destinatario@example.com"),
				"subject", "Asunto de prueba",
				"html", "<p>Cuerpo de prueba</p>");
		NotificationOutbox outbox = new NotificationOutbox(eventKey, "contact_request_received", null,
				"contact_request", UUID.randomUUID(), "destinatario@example.com", null, "Asunto de prueba",
				objectMapper.valueToTree(payload));
		return outboxRepository.save(outbox);
	}

	@Test
	void aceptadoPorElProveedorQuedaAcceptedByProvider() {
		when(resendClient.sendRaw(any(), anyString())).thenReturn(new ResendClient.SendResult("msg_123", 200));

		NotificationOutbox outbox = freshOutbox("test:accepted:" + UUID.randomUUID());
		worker.dispatchPending();

		NotificationOutbox reloaded = outboxRepository.findById(outbox.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(NotificationOutboxStatus.ACCEPTED_BY_PROVIDER);
		assertThat(reloaded.getProviderMessageId()).isEqualTo("msg_123");
		assertThat(reloaded.getAcceptedAt()).isNotNull();
	}

	@Test
	void error403EsPermanenteYFallaSinReintentar() {
		when(resendClient.sendRaw(any(), anyString())).thenThrow(
				HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY, new byte[0], null));

		NotificationOutbox outbox = freshOutbox("test:forbidden:" + UUID.randomUUID());
		worker.dispatchPending();

		NotificationOutbox reloaded = outboxRepository.findById(outbox.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(NotificationOutboxStatus.FAILED);
		assertThat(reloaded.isRetryable()).isFalse();
		assertThat(reloaded.getAttemptCount()).isEqualTo(1);
		assertThat(reloaded.getLastErrorCode()).isEqualTo("http_403");
	}

	@Test
	void error429EsTransitorioYVuelveAPendingConBackoff() {
		when(resendClient.sendRaw(any(), anyString())).thenThrow(
				HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders.EMPTY,
						new byte[0], null));

		NotificationOutbox outbox = freshOutbox("test:ratelimited:" + UUID.randomUUID());
		worker.dispatchPending();

		NotificationOutbox reloaded = outboxRepository.findById(outbox.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
		assertThat(reloaded.isRetryable()).isTrue();
		assertThat(reloaded.getAttemptCount()).isEqualTo(1);
		assertThat(reloaded.getNextAttemptAt()).isAfter(Instant.now());
	}

	@Test
	void error5xxAlTercerIntentoQuedaFailed() {
		when(resendClient.sendRaw(any(), anyString())).thenThrow(
				HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
						HttpHeaders.EMPTY, new byte[0], null));

		NotificationOutbox outbox = freshOutbox("test:servererror:" + UUID.randomUUID());

		// Intento 1: transitorio -> pending. Forzamos next_attempt_at al pasado para
		// no depender del backoff real en la prueba.
		worker.dispatchPending();
		expireBackoff(outbox.getId());

		// Intento 2: transitorio -> pending de nuevo.
		worker.dispatchPending();
		expireBackoff(outbox.getId());

		// Intento 3: tercer fallo -> failed definitivo (tope de 3 intentos).
		worker.dispatchPending();

		NotificationOutbox reloaded = outboxRepository.findById(outbox.getId()).orElseThrow();
		assertThat(reloaded.getAttemptCount()).isEqualTo(3);
		assertThat(reloaded.getStatus()).isEqualTo(NotificationOutboxStatus.FAILED);
	}

	@Test
	void timeoutEsTransitorio() {
		when(resendClient.sendRaw(any(), anyString()))
				.thenThrow(new ResourceAccessException("Timeout simulado"));

		NotificationOutbox outbox = freshOutbox("test:timeout:" + UUID.randomUUID());
		worker.dispatchPending();

		NotificationOutbox reloaded = outboxRepository.findById(outbox.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
		assertThat(reloaded.isRetryable()).isTrue();
		assertThat(reloaded.getLastErrorCode()).isEqualTo("timeout");
	}

	@Test
	void respuesta2xxSinIdEsAmbiguaYNuncaSeMarcaEntregada() {
		when(resendClient.sendRaw(any(), anyString())).thenReturn(new ResendClient.SendResult(null, 200));

		NotificationOutbox outbox = freshOutbox("test:ambiguous:" + UUID.randomUUID());
		worker.dispatchPending();

		NotificationOutbox reloaded = outboxRepository.findById(outbox.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isNotEqualTo(NotificationOutboxStatus.ACCEPTED_BY_PROVIDER);
		assertThat(reloaded.getLastErrorCode()).isEqualTo("ambiguous_response");
	}

	private void expireBackoff(UUID id) {
		NotificationOutbox outbox = outboxRepository.findById(id).orElseThrow();
		outbox.setNextAttemptAt(Instant.now().minusSeconds(1));
		outboxRepository.save(outbox);
	}
}
