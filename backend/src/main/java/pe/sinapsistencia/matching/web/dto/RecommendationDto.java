package pe.sinapsistencia.matching.web.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Recomendación de matching (HU-31) con explicación XAI (HU-32):
 * featureImportance trae los factores del modelo; reasons el lenguaje llano.
 */
public record RecommendationDto(
		String id,
		String doctorId,
		LawyerCardDto lawyer,
		int score,
		int contentScore,
		int collaborativeScore,
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
