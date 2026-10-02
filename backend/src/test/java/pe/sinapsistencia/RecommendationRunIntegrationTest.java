package pe.sinapsistencia;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/** H-02: POST /api/matching/lawyers (generación idempotente) sobre un caso real. */
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecommendationRunIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private String doctorToken;
	private String doctorId;

	@BeforeAll
	void setUp() throws Exception {
		MvcResult login = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"doctor.demo@sinapsistencia.pe\",\"password\":\"Demo123!\"}"))
				.andExpect(status().isOk())
				.andReturn();
		doctorToken = JsonPath.read(login.getResponse().getContentAsString(), "$.data.token");

		MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + doctorToken))
				.andExpect(status().isOk())
				.andReturn();
		doctorId = JsonPath.read(me.getResponse().getContentAsString(), "$.data.id");
	}

	@Test
	@DisplayName("H-02: genera, reutiliza por idempotencia y persiste historial")
	void generateRunLifecycle() throws Exception {
		MvcResult caseResult = mockMvc.perform(post("/api/legal-cases")
				.header("Authorization", "Bearer " + doctorToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Caso H-02 test\",\"description\":\"Descripción de prueba H-02\","
						+ "\"priority\":\"media\",\"context\":{\"medicalArea\":\"Psiquiatría\","
						+ "\"ageReference\":40,\"summary\":\"Contexto simulado H-02\"}}"))
				.andExpect(status().isCreated())
				.andReturn();
		String caseId = JsonPath.read(caseResult.getResponse().getContentAsString(), "$.data.id");

		String idempotencyKey = UUID.randomUUID().toString();
		MvcResult first = mockMvc.perform(post("/api/matching/lawyers")
				.header("Authorization", "Bearer " + doctorToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"caseId\":\"" + caseId + "\",\"idempotencyKey\":\"" + idempotencyKey + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.runId").exists())
				.andExpect(jsonPath("$.data.status").value("completed"))
				.andReturn();
		String runId = JsonPath.read(first.getResponse().getContentAsString(), "$.data.runId");

		// Misma clave -> mismo run (idempotencia), no una ejecución nueva.
		MvcResult second = mockMvc.perform(post("/api/matching/lawyers")
				.header("Authorization", "Bearer " + doctorToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"caseId\":\"" + caseId + "\",\"idempotencyKey\":\"" + idempotencyKey + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		String secondRunId = JsonPath.read(second.getResponse().getContentAsString(), "$.data.runId");
		org.assertj.core.api.Assertions.assertThat(secondRunId).isEqualTo(runId);

		// GET con doctorId/caseId lee el run guardado, nunca recalcula.
		mockMvc.perform(get("/api/matching/lawyers")
				.header("Authorization", "Bearer " + doctorToken)
				.param("doctorId", doctorId)
				.param("caseId", caseId))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.data.modelInfo.createdAt").isNotEmpty());

		mockMvc.perform(get("/api/matching/recommendation-runs")
				.header("Authorization", "Bearer " + doctorToken)
				.param("caseId", caseId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].runId").value(runId));

		mockMvc.perform(get("/api/matching/recommendation-runs/" + runId)
				.header("Authorization", "Bearer " + doctorToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.runId").value(runId));
	}
}
