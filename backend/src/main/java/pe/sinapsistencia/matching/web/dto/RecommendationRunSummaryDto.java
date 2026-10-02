package pe.sinapsistencia.matching.web.dto;

import java.time.Instant;

/** H-02: fila de historial (GET /api/matching/recommendation-runs?caseId=...) -- sin el detalle de recomendaciones. */
public record RecommendationRunSummaryDto(
		String runId,
		String caseId,
		String status,
		String origin,
		String modelUsed,
		String pipelineVersion,
		Instant createdAt,
		Instant completedAt,
		Integer candidateCount,
		Integer resultCount) {
}
