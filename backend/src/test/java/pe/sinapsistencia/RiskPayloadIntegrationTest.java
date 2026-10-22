package pe.sinapsistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import pe.sinapsistencia.ml.application.MlProxyService;

/**
 * Lo que el backend envía al Random Forest: solo las 7 variables estructuradas
 * (más el id del caso). El título, la descripción, el tipo de evento y el resumen
 * del contexto no viajan al servicio de riesgo (Ley 29733: minimización).
 */
@SpringBootTest(properties = {
		"spring.docker.compose.enabled=false",
		"app.notifications.worker-interval-ms=600000"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RiskPayloadIntegrationTest {

	private static final String SENTINEL_DESCRIPTION = "SENTINELA-DESCRIPCION-NO-DEBE-VIAJAR";
	private static final String SENTINEL_TITLE = "SENTINELA-TITULO-NO-DEBE-VIAJAR";
	private static final String SENTINEL_EVENT = "SENTINELA-EVENTO-NO-DEBE-VIAJAR";
	private static final String SENTINEL_SUMMARY = "SENTINELA-RESUMEN-NO-DEBE-VIAJAR";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MlProxyService mlProxyService;

	@Test
	@DisplayName("El payload al servicio de riesgo trae solo las 7 variables + case_id, sin texto libre")
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void elTextoLibreDelCasoNoViajaAlServicioDeRiesgo() throws Exception {
		when(mlProxyService.riskAssessment(any())).thenReturn(Map.of(
				"riskLevel", "moderado",
				"riskScore", 0.5101,
				"modelVersion", "rf-v2",
				"riskFactors", List.of(),
				"recommendations", List.of()));

		MvcResult login = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"doctor.demo@sinapsistencia.pe\",\"password\":\"Demo123!\"}"))
				.andExpect(status().isOk())
				.andReturn();
		String token = JsonPath.read(login.getResponse().getContentAsString(), "$.data.token");

		MvcResult created = mockMvc.perform(post("/api/legal-cases")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"" + SENTINEL_TITLE + "\",\"description\":\"" + SENTINEL_DESCRIPTION + "\","
						+ "\"medicalSpecialty\":\"Cardiología\",\"eventType\":\"" + SENTINEL_EVENT + "\","
						+ "\"priority\":\"alta\",\"perceivedUrgency\":\"alta\",\"procedureComplexity\":\"baja\","
						+ "\"documentationComplete\":true,\"informedConsent\":true,\"hasPriorComplaints\":false,"
						+ "\"context\":{\"medicalArea\":\"Cardiología\",\"ageReference\":40,"
						+ "\"eventDate\":\"2026-01-15\",\"summary\":\"" + SENTINEL_SUMMARY + "\"}}"))
				.andExpect(status().isCreated())
				.andReturn();
		String caseId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

		ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass((Class) Map.class);
		verify(mlProxyService, times(1)).riskAssessment(payload.capture());

		assertThat(payload.getValue().keySet()).containsExactlyInAnyOrder(
				"case_id", "specialty", "procedure_complexity", "priority",
				"documentation_complete", "informed_consent", "has_prior_complaints",
				"time_since_incident_days");
		String everything = payload.getValue().values().stream().map(String::valueOf).collect(Collectors.joining(" | "));
		assertThat(everything).doesNotContain("SENTINELA");

		// El resultado del RF (simulado) sí gobierna la clasificación y la prioridad sugerida.
		mockMvc.perform(get("/api/legal-cases/" + caseId + "/detail").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.classification.modelVersion").value("rf-v2"))
				.andExpect(jsonPath("$.data.caseData.priority").value("media"));
	}
}
