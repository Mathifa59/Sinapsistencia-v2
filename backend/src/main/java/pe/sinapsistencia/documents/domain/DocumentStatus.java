package pe.sinapsistencia.documents.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum DocumentStatus {

	BORRADOR("borrador"),
	PENDIENTE_FIRMA("pendiente_firma"),
	FIRMADO("firmado"),
	ARCHIVADO("archivado");

	private final String value;

	DocumentStatus(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public static DocumentStatus fromValue(String value) {
		for (DocumentStatus status : values()) {
			if (status.value.equals(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Estado de documento desconocido: " + value);
	}

	@Converter(autoApply = true)
	public static class DocumentStatusConverter implements AttributeConverter<DocumentStatus, String> {

		@Override
		public String convertToDatabaseColumn(DocumentStatus attribute) {
			return attribute == null ? null : attribute.getValue();
		}

		@Override
		public DocumentStatus convertToEntityAttribute(String dbData) {
			return dbData == null ? null : fromValue(dbData);
		}
	}
}
