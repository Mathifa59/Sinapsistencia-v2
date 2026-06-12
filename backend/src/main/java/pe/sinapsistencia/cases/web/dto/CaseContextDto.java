package pe.sinapsistencia.cases.web.dto;

import java.time.LocalDate;
import java.util.List;

import pe.sinapsistencia.cases.domain.CaseContext;

/** Contexto clínico-legal simulado (HU-12). Reemplaza al snapshot de paciente del legacy. */
public record CaseContextDto(
		String id,
		String contextCode,
		Integer ageReference,
		String medicalArea,
		LocalDate eventDate,
		String summary,
		List<String> relevantFactors,
		boolean isSimulated) {

	public static CaseContextDto from(CaseContext context) {
		return context == null ? null
				: new CaseContextDto(
						context.getId().toString(),
						context.getContextCode(),
						context.getAgeReference(),
						context.getMedicalArea(),
						context.getEventDate(),
						context.getSummary(),
						context.getRelevantFactors(),
						context.isSimulated());
	}
}
