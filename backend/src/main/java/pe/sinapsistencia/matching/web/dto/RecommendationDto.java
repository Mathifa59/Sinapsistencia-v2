package pe.sinapsistencia.matching.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Recomendación de matching (HU-31) con explicación XAI (HU-32):
 * featureImportance trae los factores del modelo; reasons el lenguaje llano.
 *
 * <p>H-03: {@code score/contentScore/performanceScore/collaborativeScore} son
 * porcentajes enteros (compatibilidad con consumidores existentes — nunca son
 * la fuente de persistencia). Los campos {@code *Raw} preservan el valor
 * normalizado [0,1] tal como lo emite el modelo, antes de redondear a
 * porcentaje. {@code collaborativeScore} queda solo por compatibilidad —
 * hoy no hay recomendador colaborativo activo (siempre 0); no se muestra
 * como parte activa del cálculo en la UI.
 */
public record RecommendationDto(
		String id,
		String doctorId,
		LawyerCardDto lawyer,
		int score,
		int contentScore,
		int performanceScore,
		@Deprecated int collaborativeScore,
		BigDecimal scoreRaw,
		BigDecimal contentScoreRaw,
		BigDecimal performanceScoreRaw,
		List<String> matchedSpecialties,
		String modelUsed,
		JsonNode featureImportance,
		List<String> reasons,
		String createdAt) {

	/** Sobre de la respuesta de recomendaciones (incluye la nota HU-43: apoyo, no decisión). */
	public record RecommendationsResponse(
			List<RecommendationDto> recommendations,
			Map<String, Object> modelInfo,
			String advisoryNote) {

		public static final String ADVISORY_NOTE =
				"Las recomendaciones del sistema son un apoyo a la decisión, no una decisión: "
						+ "la elección del profesional siempre la realiza una persona.";
	}
}
