package pe.sinapsistencia.cases.web.dto;

import java.time.LocalDate;
import java.util.List;

/** Body de PUT /api/legal-cases/{id}/edit — solo consultas en estado pendiente (HU-15). */
public record EditCaseRequest(
		String title,
		String description,
		String priority,
		String medicalSpecialty,
		String eventType,
		String perceivedUrgency,
		String notes,
		CreateCaseRequest.ContextPayload context) {
}
