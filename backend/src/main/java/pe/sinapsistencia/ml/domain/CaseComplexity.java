package pe.sinapsistencia.ml.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Complejidad estimada por la clasificación ML (HU-29). */
public enum CaseComplexity {

	BAJA("baja"),
	MEDIA("media"),
	ALTA("alta");

	private final String value;

	CaseComplexity(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public static CaseComplexity fromValue(String value) {
		for (CaseComplexity complexity : values()) {
			if (complexity.value.equals(value)) {
				return complexity;
			}
		}
		throw new IllegalArgumentException("Complejidad desconocida: " + value);
	}

	@Converter(autoApply = true)
	public static class CaseComplexityConverter implements AttributeConverter<CaseComplexity, String> {

		@Override
		public String convertToDatabaseColumn(CaseComplexity attribute) {
			return attribute == null ? null : attribute.getValue();
		}

		@Override
		public CaseComplexity convertToEntityAttribute(String dbData) {
			return dbData == null ? null : fromValue(dbData);
		}
	}
}
