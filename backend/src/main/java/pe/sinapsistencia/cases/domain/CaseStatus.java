package pe.sinapsistencia.cases.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Flujo de 6 estados de la consulta médico-legal (HU-16):
 * Pendiente → Clasificada → Asignada → En revisión → Respondida → Cerrada.
 */
public enum CaseStatus {

	PENDIENTE("pendiente"),
	CLASIFICADA("clasificada"),
	ASIGNADA("asignada"),
	EN_REVISION("en_revision"),
	RESPONDIDA("respondida"),
	CERRADA("cerrada");

	private final String value;

	CaseStatus(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public static CaseStatus fromValue(String value) {
		for (CaseStatus status : values()) {
			if (status.value.equals(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Estado de consulta desconocido: " + value);
	}

	@Converter(autoApply = true)
	public static class CaseStatusConverter implements AttributeConverter<CaseStatus, String> {

		@Override
		public String convertToDatabaseColumn(CaseStatus attribute) {
			return attribute == null ? null : attribute.getValue();
		}

		@Override
		public CaseStatus convertToEntityAttribute(String dbData) {
			return dbData == null ? null : fromValue(dbData);
		}
	}
}
