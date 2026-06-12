package pe.sinapsistencia.matching.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum ContactRequestStatus {

	PENDIENTE("pendiente"),
	ACEPTADO("aceptado"),
	RECHAZADO("rechazado"),
	CANCELADO("cancelado");

	private final String value;

	ContactRequestStatus(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public static ContactRequestStatus fromValue(String value) {
		for (ContactRequestStatus status : values()) {
			if (status.value.equals(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Estado de solicitud desconocido: " + value);
	}

	@Converter(autoApply = true)
	public static class ContactRequestStatusConverter implements AttributeConverter<ContactRequestStatus, String> {

		@Override
		public String convertToDatabaseColumn(ContactRequestStatus attribute) {
			return attribute == null ? null : attribute.getValue();
		}

		@Override
		public ContactRequestStatus convertToEntityAttribute(String dbData) {
			return dbData == null ? null : fromValue(dbData);
		}
	}
}
