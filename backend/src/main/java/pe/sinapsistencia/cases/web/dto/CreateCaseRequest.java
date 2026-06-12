package pe.sinapsistencia.cases.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Body de POST /api/legal-cases (HU-11 + HU-12).
 * El doctor se toma SIEMPRE del token (ownership), nunca del body.
 */
public record CreateCaseRequest(
		String title,
		String description,
		String priority,
		String medicalSpecialty,
		String eventType,
		String perceivedUrgency,
		String notes,
		ContextPayload context) {

	/** Contexto clínico-legal simulado, sin datos identificables (Ley 29733). */
	public record ContextPayload(
			String contextCode,
			Integer ageReference,
			String medicalArea,
			LocalDate eventDate,
			String summary,
			List<String> relevantFactors) {
	}
}
