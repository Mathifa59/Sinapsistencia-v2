package pe.sinapsistencia.cases.application;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H-04: {@code isValidMlRisk} no debe convertir un campo faltante o fuera de
 * rango en un valor inventado (riskScore=0, nivel desconocido) -- una
 * respuesta ML malformada debe tratarse como fallo del servicio.
 */
class CaseClassificationServiceTest {

	private static Map<String, Object> validRisk() {
		Map<String, Object> risk = new HashMap<>();
		risk.put("riskLevel", "critico");
		risk.put("riskScore", 0.9952);
		risk.put("modelVersion", "rf-v2");
		return risk;
	}

	@Test
	void aceptaUnaRespuestaCompletaYValida() {
		assertTrue(CaseClassificationService.isValidMlRisk(validRisk()));
	}

	@ParameterizedTest
	@MethodSource("fixturesInvalidas")
	void rechazaRespuestasIncompletasOFueraDeRango(String caso, Map<String, Object> risk) {
		assertFalse(CaseClassificationService.isValidMlRisk(risk), caso);
	}

	static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> fixturesInvalidas() {
		Map<String, Object> sinScore = validRisk();
		sinScore.remove("riskScore");

		Map<String, Object> scoreNoNumerico = validRisk();
		scoreNoNumerico.put("riskScore", "alto"); // string en vez de numero

		Map<String, Object> scoreFueraDeRango = validRisk();
		scoreFueraDeRango.put("riskScore", 1.5);

		Map<String, Object> scoreNegativo = validRisk();
		scoreNegativo.put("riskScore", -0.1);

		Map<String, Object> scoreNaN = validRisk();
		scoreNaN.put("riskScore", Double.NaN);

		Map<String, Object> scoreInfinito = validRisk();
		scoreInfinito.put("riskScore", Double.POSITIVE_INFINITY);

		Map<String, Object> nivelNoAdmitido = validRisk();
		nivelNoAdmitido.put("riskLevel", "desconocido");

		Map<String, Object> sinNivel = validRisk();
		sinNivel.remove("riskLevel");

		Map<String, Object> sinVersion = validRisk();
		sinVersion.remove("modelVersion");

		Map<String, Object> versionVacia = validRisk();
		versionVacia.put("modelVersion", "");

		return java.util.stream.Stream.of(
				org.junit.jupiter.params.provider.Arguments.of("riskScore ausente", sinScore),
				org.junit.jupiter.params.provider.Arguments.of("riskScore no numérico", scoreNoNumerico),
				org.junit.jupiter.params.provider.Arguments.of("riskScore > 1", scoreFueraDeRango),
				org.junit.jupiter.params.provider.Arguments.of("riskScore negativo", scoreNegativo),
				org.junit.jupiter.params.provider.Arguments.of("riskScore NaN", scoreNaN),
				org.junit.jupiter.params.provider.Arguments.of("riskScore infinito", scoreInfinito),
				org.junit.jupiter.params.provider.Arguments.of("riskLevel no admitido", nivelNoAdmitido),
				org.junit.jupiter.params.provider.Arguments.of("riskLevel ausente", sinNivel),
				org.junit.jupiter.params.provider.Arguments.of("modelVersion ausente", sinVersion),
				org.junit.jupiter.params.provider.Arguments.of("modelVersion vacío", versionVacia));
	}
}
