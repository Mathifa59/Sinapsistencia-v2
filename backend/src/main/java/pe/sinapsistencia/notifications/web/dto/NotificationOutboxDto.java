package pe.sinapsistencia.notifications.web.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import pe.sinapsistencia.notifications.domain.NotificationOutbox;

/**
 * H-06: estado público de un aviso (GET /api/legal-cases/{id}/notifications).
 * Nunca transporta {@code recipient}/{@code replyTo}/{@code subject}/{@code payload}
 * (privados) ni el API key -- {@code providerMessageId}/{@code providerHttpStatus}
 * solo se incluyen para un administrador.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationOutboxDto(
		String id,
		String type,
		String resourceType,
		String resourceId,
		String status,
		int attemptCount,
		boolean retryable,
		String lastErrorCode,
		String lastErrorMessage,
		Instant createdAt,
		Instant updatedAt,
		Instant acceptedAt,
		String providerMessageId,
		Integer providerHttpStatus) {

	public static NotificationOutboxDto from(NotificationOutbox o, boolean includeProviderDetail) {
		return new NotificationOutboxDto(
				o.getId().toString(),
				o.getType(),
				o.getResourceType(),
				o.getResourceId().toString(),
				o.getStatus().getValue(),
				o.getAttemptCount(),
				o.isRetryable(),
				o.getLastErrorCode(),
				o.getLastErrorMessage(),
				o.getCreatedAt(),
				o.getUpdatedAt(),
				o.getAcceptedAt(),
				includeProviderDetail ? o.getProviderMessageId() : null,
				includeProviderDetail ? o.getProviderHttpStatus() : null);
	}
}
