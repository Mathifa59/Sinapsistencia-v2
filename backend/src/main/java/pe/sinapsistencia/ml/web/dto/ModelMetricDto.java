package pe.sinapsistencia.ml.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import pe.sinapsistencia.ml.domain.ModelMetric;

public record ModelMetricDto(
		String id,
		String modelName,
		String modelVersion,
		BigDecimal precisionScore,
		BigDecimal recallScore,
		BigDecimal f1Score,
		BigDecimal matchingRelevanceRate,
		Integer avgResponseTimeMs,
		Integer datasetSize,
		String notes,
		Instant evaluatedAt) {

	public static ModelMetricDto from(ModelMetric metric) {
		return new ModelMetricDto(
				metric.getId().toString(),
				metric.getModelName(),
				metric.getModelVersion(),
				metric.getPrecisionScore(),
				metric.getRecallScore(),
				metric.getF1Score(),
				metric.getMatchingRelevanceRate(),
				metric.getAvgResponseTimeMs(),
				metric.getDatasetSize(),
				metric.getNotes(),
				metric.getEvaluatedAt());
	}
}
