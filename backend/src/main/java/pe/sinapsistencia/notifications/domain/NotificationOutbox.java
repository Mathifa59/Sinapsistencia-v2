package pe.sinapsistencia.notifications.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * H-06: una fila por INTENTO de aviso. Se crea en la transacción de negocio
 * ({@code ContactRequestService}/{@code CaseClassificationService}) y
 * {@code NotificationWorker} la despacha después del commit -- un rollback de
 * la operación de negocio se lleva también esta fila, nunca queda un aviso de
 * algo que no pasó.
 */
@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/** contact_request_received:&lt;requestId&gt; | contact_request_answered:&lt;requestId&gt;:&lt;status&gt; | risk_alert:&lt;classificationId&gt;. */
	@Column(name = "event_key", nullable = false, length = 200, unique = true)
	private String eventKey;

	@Column(nullable = false, length = 50)
	private String type;

	@Column(name = "case_id")
	private UUID caseId;

	/** 'contact_request' | 'ml_classification' -- junto a resourceId, validado en servicio (una FK no puede apuntar a dos tablas). */
	@Column(name = "resource_type", nullable = false, length = 50)
	private String resourceType;

	@Column(name = "resource_id", nullable = false)
	private UUID resourceId;

	@Column(nullable = false, columnDefinition = "text")
	private String recipient;

	@Column(name = "reply_to", columnDefinition = "text")
	private String replyTo;

	@Column(nullable = false, columnDefinition = "text")
	private String subject;

	/** Fotografía del body HTTP efectivo (incluye el remitente resuelto) -- fijada en el primer intento. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private JsonNode payload;

	@Column(nullable = false, length = 30)
	private NotificationOutboxStatus status = NotificationOutboxStatus.PENDING;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount = 0;

	@Column(name = "next_attempt_at")
	private Instant nextAttemptAt;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "worker_token")
	private UUID workerToken;

	@Column(name = "provider_message_id", length = 200)
	private String providerMessageId;

	@Column(name = "provider_http_status")
	private Integer providerHttpStatus;

	@Column(name = "last_error_code", length = 100)
	private String lastErrorCode;

	@Column(name = "last_error_message", columnDefinition = "text")
	private String lastErrorMessage;

	@Column(nullable = false)
	private boolean retryable = false;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	protected NotificationOutbox() {
	}

	public NotificationOutbox(String eventKey, String type, UUID caseId, String resourceType, UUID resourceId,
			String recipient, String replyTo, String subject, JsonNode payload) {
		this.eventKey = eventKey;
		this.type = type;
		this.caseId = caseId;
		this.resourceType = resourceType;
		this.resourceId = resourceId;
		this.recipient = recipient;
		this.replyTo = replyTo;
		this.subject = subject;
		this.payload = payload;
	}

	public UUID getId() {
		return id;
	}

	public String getEventKey() {
		return eventKey;
	}

	public String getType() {
		return type;
	}

	public UUID getCaseId() {
		return caseId;
	}

	public String getResourceType() {
		return resourceType;
	}

	public UUID getResourceId() {
		return resourceId;
	}

	public String getRecipient() {
		return recipient;
	}

	public String getReplyTo() {
		return replyTo;
	}

	public String getSubject() {
		return subject;
	}

	public JsonNode getPayload() {
		return payload;
	}

	public NotificationOutboxStatus getStatus() {
		return status;
	}

	public void setStatus(NotificationOutboxStatus status) {
		this.status = status;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public void setAttemptCount(int attemptCount) {
		this.attemptCount = attemptCount;
	}

	public Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	public void setNextAttemptAt(Instant nextAttemptAt) {
		this.nextAttemptAt = nextAttemptAt;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}

	public void setLockedUntil(Instant lockedUntil) {
		this.lockedUntil = lockedUntil;
	}

	public UUID getWorkerToken() {
		return workerToken;
	}

	public void setWorkerToken(UUID workerToken) {
		this.workerToken = workerToken;
	}

	public String getProviderMessageId() {
		return providerMessageId;
	}

	public void setProviderMessageId(String providerMessageId) {
		this.providerMessageId = providerMessageId;
	}

	public Integer getProviderHttpStatus() {
		return providerHttpStatus;
	}

	public void setProviderHttpStatus(Integer providerHttpStatus) {
		this.providerHttpStatus = providerHttpStatus;
	}

	public String getLastErrorCode() {
		return lastErrorCode;
	}

	public void setLastErrorCode(String lastErrorCode) {
		this.lastErrorCode = lastErrorCode;
	}

	public String getLastErrorMessage() {
		return lastErrorMessage;
	}

	public void setLastErrorMessage(String lastErrorMessage) {
		this.lastErrorMessage = lastErrorMessage;
	}

	public boolean isRetryable() {
		return retryable;
	}

	public void setRetryable(boolean retryable) {
		this.retryable = retryable;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getAcceptedAt() {
		return acceptedAt;
	}

	public void setAcceptedAt(Instant acceptedAt) {
		this.acceptedAt = acceptedAt;
	}
}
