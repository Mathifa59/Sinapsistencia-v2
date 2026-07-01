package pe.sinapsistencia.cases.web.dto;

import java.time.Instant;
import java.time.LocalDate;

import pe.sinapsistencia.cases.domain.CaseEvent;

public record CaseEventDto(
		String id,
		String caseId,
		LocalDate eventDate,
		String eventType,
		String description,
		boolean isSimulated,
		String createdByName,
		Instant createdAt) {

	public static CaseEventDto from(CaseEvent event) {
		return new CaseEventDto(
				event.getId().toString(),
				event.getLegalCase().getId().toString(),
				event.getEventDate(),
				event.getEventType(),
				event.getDescription(),
				event.isSimulated(),
				event.getCreatedBy().getName(),
				event.getCreatedAt());
	}
}
