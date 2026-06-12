package pe.sinapsistencia.cases.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Prioridad de la consulta (HU-30). También se usa para urgencia percibida/clasificada. */
public enum CasePriority {

	BAJA("baja"),
	MEDIA("media"),
	ALTA("alta"),
	CRITICA("critica");

	private final String value;

	CasePriority(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public static CasePriority fromValue(String value) {
		for (CasePriority priority : values()) {
			if (priority.value.equals(value)) {
				return priority;
			}
		}
		throw new IllegalArgumentException("Prioridad desconocida: " + value);
	}

	@Converter(autoApply = true)
	public static class CasePriorityConverter implements AttributeConverter<CasePriority, String> {

		@Override
		public String convertToDatabaseColumn(CasePriority attribute) {
			return attribute == null ? null : attribute.getValue();
		}

		@Override
		public CasePriority convertToEntityAttribute(String dbData) {
			return dbData == null ? null : fromValue(dbData);
		}
	}
}
