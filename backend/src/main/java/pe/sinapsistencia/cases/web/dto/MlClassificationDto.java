package pe.sinapsistencia.cases.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import pe.sinapsistencia.ml.domain.MlClassification;

public record MlClassificationDto(
		String id,
		String caseType,
		String urgency,
		String complexity,
		String suggestedSpecialty,
		BigDecimal confidence,
		String modelVersion,
		Integer responseTimeMs,
		Instant createdAt) {

	public static MlClassificationDto from(MlClassification c) {
		return new MlClassificationDto(
				c.getId().toString(),
				c.getCaseType(),
				c.getUrgency() == null ? null : c.getUrgency().getValue(),
				c.getComplexity() == null ? null : c.getComplexity().getValue(),
				c.getSuggestedSpecialty(),
				c.getConfidence(),
				c.getModelVersion(),
				c.getResponseTimeMs(),
				c.getCreatedAt());
	}
}
