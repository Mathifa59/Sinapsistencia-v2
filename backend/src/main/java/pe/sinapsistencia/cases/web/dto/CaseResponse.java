package pe.sinapsistencia.cases.web.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import pe.sinapsistencia.cases.domain.CaseContext;
import pe.sinapsistencia.cases.domain.LegalCase;

/**
 * Shape de la consulta en la API — espeja el del legacy (snapshots aplanados
 * doctor/lawyer) con los campos académicos añadidos y el contexto simulado en
 * lugar del paciente.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CaseResponse(
		String id,
		String title,
		String description,
		String status,
		String priority,
		String priorityJustification,
		String medicalSpecialty,
		String eventType,
		String perceivedUrgency,
		String doctorId,
		PersonSnapshot doctor,
		String lawyerId,
		PersonSnapshot lawyer,
		CaseContextDto context,
		String notes,
		Instant createdAt,
		Instant updatedAt) {

	public static CaseResponse from(LegalCase legalCase, CaseContext context) {
		return new CaseResponse(
				legalCase.getId().toString(),
				legalCase.getTitle(),
				legalCase.getDescription(),
				legalCase.getStatus().getValue(),
				legalCase.getPriority().getValue(),
				legalCase.getPriorityJustification(),
				legalCase.getMedicalSpecialty(),
				legalCase.getEventType(),
				legalCase.getPerceivedUrgency() == null ? null : legalCase.getPerceivedUrgency().getValue(),
				legalCase.getDoctor().getId().toString(),
				PersonSnapshot.from(legalCase.getDoctor()),
				legalCase.getLawyer() == null ? null : legalCase.getLawyer().getId().toString(),
				PersonSnapshot.from(legalCase.getLawyer()),
				CaseContextDto.from(context),
				legalCase.getNotes(),
				legalCase.getCreatedAt(),
				legalCase.getUpdatedAt());
	}
}
