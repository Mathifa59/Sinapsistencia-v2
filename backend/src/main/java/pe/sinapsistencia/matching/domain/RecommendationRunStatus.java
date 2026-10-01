package pe.sinapsistencia.matching.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Estado de una ejecución de matching persistida (H-02). */
public enum RecommendationRunStatus {

	PROCESSING("processing"),
	COMPLETED("completed"),
	FAILED("failed");

	private final String value;

	RecommendationRunStatus(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public static RecommendationRunStatus fromValue(String value) {
		for (RecommendationRunStatus status : values()) {
			if (status.value.equals(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Estado de ejecución desconocido: " + value);
	}

	@Converter(autoApply = true)
	public static class RecommendationRunStatusConverter implements AttributeConverter<RecommendationRunStatus, String> {

		@Override
		public String convertToDatabaseColumn(RecommendationRunStatus attribute) {
			return attribute == null ? null : attribute.getValue();
		}

		@Override
		public RecommendationRunStatus convertToEntityAttribute(String dbData) {
			return dbData == null ? null : fromValue(dbData);
		}
	}
}
