package pe.sinapsistencia.documents.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum DocumentType {

	HISTORIA_CLINICA("historia_clinica"),
	CONSENTIMIENTO_INFORMADO("consentimiento_informado"),
	INFORME_MEDICO("informe_medico"),
	RECETA("receta"),
	ORDEN_LABORATORIO("orden_laboratorio"),
	CERTIFICADO_MEDICO("certificado_medico"),
	DOCUMENTO_LEGAL("documento_legal"),
	OTRO("otro");

	private final String value;

	DocumentType(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public static DocumentType fromValue(String value) {
		for (DocumentType type : values()) {
			if (type.value.equals(value)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Tipo de documento desconocido: " + value);
	}

	@Converter(autoApply = true)
	public static class DocumentTypeConverter implements AttributeConverter<DocumentType, String> {

		@Override
		public String convertToDatabaseColumn(DocumentType attribute) {
			return attribute == null ? null : attribute.getValue();
		}

		@Override
		public DocumentType convertToEntityAttribute(String dbData) {
			return dbData == null ? null : fromValue(dbData);
		}
	}
}
