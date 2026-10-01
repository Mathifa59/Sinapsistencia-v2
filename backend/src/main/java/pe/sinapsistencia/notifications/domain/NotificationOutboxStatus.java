package pe.sinapsistencia.notifications.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * H-06: estado de un aviso en el outbox. {@code delivered}/{@code bounced} NO
 * existen a propósito -- sin webhook validado del proveedor, "aceptado por el
 * proveedor" es la entrega más fuerte que el sistema puede afirmar sin fingir evidencia.
 */
public enum NotificationOutboxStatus {

	PENDING("pending"),
	PROCESSING("processing"),
	ACCEPTED_BY_PROVIDER("accepted_by_provider"),
	FAILED("failed"),
	SKIPPED("skipped");

	private final String value;

	NotificationOutboxStatus(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public static NotificationOutboxStatus fromValue(String value) {
		for (NotificationOutboxStatus status : values()) {
			if (status.value.equals(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Estado de notificación desconocido: " + value);
	}

	@Converter(autoApply = true)
	public static class NotificationOutboxStatusConverter
			implements AttributeConverter<NotificationOutboxStatus, String> {

		@Override
		public String convertToDatabaseColumn(NotificationOutboxStatus attribute) {
			return attribute == null ? null : attribute.getValue();
		}

		@Override
		public NotificationOutboxStatus convertToEntityAttribute(String dbData) {
			return dbData == null ? null : fromValue(dbData);
		}
	}
}
