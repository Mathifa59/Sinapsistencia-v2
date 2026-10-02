package pe.sinapsistencia.cases.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import pe.sinapsistencia.ml.domain.MlClassification;

public record MlClassificationDto(
		String id,
		String caseType,
		String urgency,
		String complexity,
		String suggestedSpecialty,
		BigDecimal confidence,
		BigDecimal riskScore,
		String riskLevel,
		String riskFactorsJson,
		String modelVersion,
		Integer responseTimeMs,
		// H-05: fotografía de las 7 entradas evaluadas + metadata; NULL en registros
		// anteriores a este flujo -- nunca se reconstruye.
		JsonNode inputSnapshot,
		String pipelineVersion,
		Instant createdAt) {

	public static MlClassificationDto from(MlClassification c) {
		return new MlClassificationDto(
				c.getId().toString(),
				c.getCaseType(),
				c.getUrgency() == null ? null : c.getUrgency().getValue(),
				c.getComplexity() == null ? null : c.getComplexity().getValue(),
				c.getSuggestedSpecialty(),
				c.getConfidence(),
				c.getRiskScore(),
				c.getRiskLevel(),
				c.getRiskFactors(),
				c.getModelVersion(),
				c.getResponseTimeMs(),
				c.getInputSnapshot(),
				c.getPipelineVersion(),
				c.getCreatedAt());
	}
}
