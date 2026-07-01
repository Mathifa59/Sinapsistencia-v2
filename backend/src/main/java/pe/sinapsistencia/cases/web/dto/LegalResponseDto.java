package pe.sinapsistencia.cases.web.dto;

import java.time.Instant;

import pe.sinapsistencia.cases.domain.LegalResponse;

public record LegalResponseDto(
		String id,
		String caseId,
		String lawyerId,
		String lawyerName,
		String content,
		String recommendations,
		String observations,
		boolean isReviewed,
		Instant createdAt,
		Instant updatedAt) {

	public static LegalResponseDto from(LegalResponse response) {
		return new LegalResponseDto(
				response.getId().toString(),
				response.getLegalCase().getId().toString(),
				response.getLawyer().getId().toString(),
				response.getLawyer().getName(),
				response.getContent(),
				response.getRecommendations(),
				response.getObservations(),
				response.isReviewed(),
				response.getCreatedAt(),
				response.getUpdatedAt());
	}
}
