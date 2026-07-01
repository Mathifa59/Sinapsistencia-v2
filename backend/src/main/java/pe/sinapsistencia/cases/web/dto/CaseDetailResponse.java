package pe.sinapsistencia.cases.web.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import pe.sinapsistencia.matching.web.dto.RecommendationDto.RecommendationsResponse;

/** Detalle completo de consulta (HU-14, HU-33). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CaseDetailResponse(
		CaseResponse caseData,
		MlClassificationDto classification,
		List<LegalResponseDto> responses,
		List<CaseEventDto> events,
		List<TimelineEntryDto> timeline,
		RecommendationsResponse recommendations,
		String advisoryNote) {

	public static final String ADVISORY_NOTE = RecommendationsResponse.ADVISORY_NOTE;
}
