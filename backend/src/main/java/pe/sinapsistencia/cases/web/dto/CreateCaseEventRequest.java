package pe.sinapsistencia.cases.web.dto;

import java.time.LocalDate;

/** Body de POST /api/legal-cases/{id}/events (HU-26). */
public record CreateCaseEventRequest(
		LocalDate eventDate,
		String eventType,
		String description) {
}
