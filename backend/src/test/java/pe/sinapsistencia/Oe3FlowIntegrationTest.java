package pe.sinapsistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

/**
 * Smoke tests del flujo OE3: consulta → clasificación ML → contacto → respuesta → cierre.
 * Valida OE3-I2 (historial consultable y tiempo de respuesta en listados).
 */
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Oe3FlowIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private String doctorToken;
	private String lawyerToken;
	private String doctorId;
	private String lawyerId;

	@BeforeAll
	void setUp() throws Exception {
		doctorToken = loginDemo("doctor");
		lawyerToken = loginDemo("lawyer");
		doctorId = whoAmI(doctorToken);
		lawyerId = whoAmI(lawyerToken);
	}

	@Test
	@DisplayName("OE3: flujo completo médico-abogado con clasificación ML")
	void fullOe3CaseLifecycle() throws Exception {
		String caseId = createCase(doctorToken, "Consulta OE3 integración");

		mockMvc.perform(get("/api/legal-cases/" + caseId + "/detail")
				.header("Authorization", "Bearer " + doctorToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.classification").exists())
				.andExpect(jsonPath("$.data.caseData.status").value("clasificada"));

		String requestId = createContactRequest(doctorToken, lawyerId, caseId);

		mockMvc.perform(patch("/api/matching/contact-requests")
				.header("Authorization", "Bearer " + lawyerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"requestId\":\"" + requestId + "\",\"status\":\"aceptado\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/legal-cases/" + caseId + "/detail")
				.header("Authorization", "Bearer " + lawyerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.caseData.status").value("asignada"));

		mockMvc.perform(post("/api/legal-cases/" + caseId + "/start-review")
				.header("Authorization", "Bearer " + lawyerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("en_revision"));

		mockMvc.perform(post("/api/legal-cases/" + caseId + "/responses")
				.header("Authorization", "Bearer " + lawyerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"Orientación médico-legal preliminar para la consulta OE3.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").exists());

		mockMvc.perform(get("/api/legal-cases/" + caseId + "/detail")
				.header("Authorization", "Bearer " + doctorToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.caseData.status").value("respondida"))
				.andExpect(jsonPath("$.data.timeline").isArray());

		mockMvc.perform(post("/api/legal-cases/" + caseId + "/close")
				.header("Authorization", "Bearer " + doctorToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"Caso resuelto en demo OE3\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("cerrada"));
	}

	@Test
	@DisplayName("OE3-I2: historial consultable y listado < 2 s")
	void caseListPerformanceAndHistory() throws Exception {
		createCase(doctorToken, "Consulta historial OE3");

		long start = System.currentTimeMillis();
		MvcResult list = mockMvc.perform(get("/api/legal-cases")
				.header("Authorization", "Bearer " + doctorToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.data").isArray())
				.andReturn();
		long elapsed = System.currentTimeMillis() - start;

		assertThat(elapsed).isLessThan(2000L);
		assertThat(list.getResponse().getContentAsString()).contains("Consulta historial OE3");

		mockMvc.perform(get("/api/legal-cases")
				.header("Authorization", "Bearer " + lawyerToken))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("HU-04: recuperación de contraseña")
	void passwordResetFlow() throws Exception {
		MvcResult forgot = mockMvc.perform(post("/api/auth/forgot-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"doctor.demo@sinapsistencia.pe\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.resetToken").exists())
				.andReturn();

		String token = JsonPath.read(forgot.getResponse().getContentAsString(), "$.data.resetToken");

		mockMvc.perform(post("/api/auth/reset-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"doctor.demo@sinapsistencia.pe\",\"token\":\"" + token
						+ "\",\"newPassword\":\"Demo123!\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"doctor.demo@sinapsistencia.pe\",\"password\":\"Demo123!\"}"))
				.andExpect(status().isOk());
	}

	private String loginDemo(String role) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"" + role + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
	}

	private String whoAmI(String token) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private String createCase(String token, String title) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/legal-cases")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"" + title + "\",\"description\":\"Descripción de prueba OE3\","
						+ "\"priority\":\"media\",\"context\":{\"medicalArea\":\"Cardiología\","
						+ "\"ageReference\":50,\"summary\":\"Contexto simulado OE3\"}}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("clasificada"))
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}

	private String createContactRequest(String doctorToken, String lawyerId, String caseId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/matching/contact-requests")
				.header("Authorization", "Bearer " + doctorToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fromDoctorId\":\"" + doctorId + "\",\"toLawyerId\":\"" + lawyerId
						+ "\",\"message\":\"Solicitud OE3\",\"caseId\":\"" + caseId + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}
}
