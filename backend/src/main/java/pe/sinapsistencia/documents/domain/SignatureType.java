package pe.sinapsistencia.documents.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum SignatureType {

	DIGITAL("digital"),
	HUELLA("huella"),
	FIRMA_MANUSCRITA("firma_manuscrita");

	private final String value;

	SignatureType(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public static SignatureType fromValue(String value) {
		for (SignatureType type : values()) {
			if (type.value.equals(value)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Tipo de firma desconocido: " + value);
	}

	@Converter(autoApply = true)
	public static class SignatureTypeConverter implements AttributeConverter<SignatureType, String> {

		@Override
		public String convertToDatabaseColumn(SignatureType attribute) {
			return attribute == null ? null : attribute.getValue();
		}

		@Override
		public SignatureType convertToEntityAttribute(String dbData) {
			return dbData == null ? null : fromValue(dbData);
		}
	}
}
