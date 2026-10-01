package pe.sinapsistencia.matching.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** H-02: ejecución de matching completa, con sus recomendaciones (IDs reales, no "rec-..."). */
public record RecommendationRunDto(
		String runId,
		String caseId,
		String status,
		String origin,
		String modelUsed,
		String pipelineVersion,
		Instant createdAt,
		Instant completedAt,
		Map<String, Object> weights,
		List<RecommendationDto> recommendations,
		String advisoryNote) {

	public static final String ADVISORY_NOTE = RecommendationDto.RecommendationsResponse.ADVISORY_NOTE;
}
