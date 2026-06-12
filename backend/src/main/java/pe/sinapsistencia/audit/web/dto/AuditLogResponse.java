package pe.sinapsistencia.audit.web.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import pe.sinapsistencia.audit.domain.AuditLog;

/**
 * Shape del log de auditoría — espeja el legacy: userName "Sistema" si no hay
 * usuario, description sale de details.description o "{action} en {resource}".
 */
public record AuditLogResponse(
		String id,
		String userId,
		String userName,
		String userRole,
		String action,
		String resource,
		String resourceId,
		String description,
		JsonNode details,
		String ipAddress,
		Instant createdAt) {

	public static AuditLogResponse from(AuditLog log, JsonNode details) {
		String description = details != null && details.hasNonNull("description")
				? details.get("description").asText()
				: log.getAction() + " en " + log.getResource();

		return new AuditLogResponse(
				log.getId().toString(),
				log.getUser() == null ? null : log.getUser().getId().toString(),
				log.getUser() == null ? "Sistema" : log.getUser().getName(),
				log.getUser() == null ? "admin" : log.getUser().getRole().getValue(),
				log.getAction(),
				log.getResource(),
				log.getResourceId(),
				description,
				details,
				log.getIpAddress(),
				log.getCreatedAt());
	}
}
