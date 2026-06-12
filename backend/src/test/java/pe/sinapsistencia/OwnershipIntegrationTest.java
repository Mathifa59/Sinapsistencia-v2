package pe.sinapsistencia;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Tests del reemplazo de RLS: el ownership en la capa de servicio FALLA
 * ABIERTO si se olvida, así que cada regla crítica tiene su test:
 *   - un doctor NUNCA ve consultas/documentos de otro doctor
 *   - un lawyer NUNCA ve consultas asignadas a otro lawyer
 *   - users/audit son solo de admin
 *   - el perfil de otro usuario es inaccesible salvo admin
 */
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnershipIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private String doctorAToken;   // demo seedeado
	private String doctorBToken;   // registrado en el test
	private String lawyer1Token;   // demo seedeado
	private String lawyer2Token;   // registrado en el test
	private String adminToken;     // demo seedeado
	private String doctorAId;
	private String lawyer1Id;

	@BeforeAll
	void setUp() throws Exception {
		registerUser("doctor", "doctor.b@test.pe", "{\"specialty\":\"Cardiología\"}");
		registerUser("lawyer", "lawyer.2@test.pe",
				"{\"legalSpecialties\":[\"Derecho Médico\"],\"medicalAreas\":[\"Cardiología\"]}");

		doctorAToken = loginDemo("doctor");
		lawyer1Token = loginDemo("lawyer");
		adminToken = loginDemo("admin");
		doctorBToken = loginEmail("doctor.b@test.pe");
		lawyer2Token = loginEmail("lawyer.2@test.pe");

		doctorAId = whoAmI(doctorAToken);
		lawyer1Id = whoAmI(lawyer1Token);
	}

	// ── Consultas ───────────────────────────────────────────────────────────

	@Test
	@DisplayName("Un doctor NO ve la consulta de otro doctor (403) ni la encuentra en su listado")
	void doctorCannotSeeOthersCase() throws Exception {
		String caseId = createCase(doctorAToken, "Caso privado de A");

		mockMvc.perform(get("/api/legal-cases/" + caseId).header("Authorization", "Bearer " + doctorBToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.success").value(false));

		MvcResult list = mockMvc.perform(get("/api/legal-cases")
				.header("Authorization", "Bearer " + doctorBToken))
				.andExpect(status().isOk())
				.andReturn();
		String body = list.getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(body).doesNotContain(caseId);
	}

	@Test
	@DisplayName("El query param doctorId NO permite a un doctor espiar consultas ajenas")
	void doctorIdParamCannotBypassOwnership() throws Exception {
		String caseId = createCase(doctorAToken, "Caso espía");

		MvcResult list = mockMvc.perform(get("/api/legal-cases")
				.param("doctorId", doctorAId)
				.header("Authorization", "Bearer " + doctorBToken))
				.andExpect(status().isOk())
				.andReturn();
		org.assertj.core.api.Assertions.assertThat(list.getResponse().getContentAsString())
				.doesNotContain(caseId);
	}

	@Test
	@DisplayName("Un lawyer ve consultas sin asignar, pero NO las asignadas a otro lawyer")
	void lawyerCannotSeeOthersAssignedCase() throws Exception {
		String caseId = createCase(doctorAToken, "Caso para asignar");

		// Sin asignar: ambos abogados pueden verla (es candidata a matching)
		mockMvc.perform(get("/api/legal-cases/" + caseId).header("Authorization", "Bearer " + lawyer2Token))
				.andExpect(status().isOk());

		// El doctor la asigna al lawyer demo (L1)
		mockMvc.perform(put("/api/legal-cases/" + caseId)
				.header("Authorization", "Bearer " + doctorAToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lawyerId\":\"" + lawyer1Id + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("asignada"));

		// L1 (asignado) la ve; L2 ya NO
		mockMvc.perform(get("/api/legal-cases/" + caseId).header("Authorization", "Bearer " + lawyer1Token))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/legal-cases/" + caseId).header("Authorization", "Bearer " + lawyer2Token))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Un doctor NO puede modificar la consulta de otro doctor")
	void doctorCannotModifyOthersCase() throws Exception {
		String caseId = createCase(doctorAToken, "Caso inmutable para B");

		mockMvc.perform(put("/api/legal-cases/" + caseId)
				.header("Authorization", "Bearer " + doctorBToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"cerrada\"}"))
				.andExpect(status().isForbidden());
	}

	// ── Documentos ──────────────────────────────────────────────────────────

	@Test
	@DisplayName("Un doctor NO ve el documento de otro doctor; admin sí")
	void doctorCannotSeeOthersDocument() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/documents")
				.header("Authorization", "Bearer " + doctorAToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Informe privado\",\"type\":\"informe_medico\",\"initialContent\":\"x\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		String docId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(get("/api/documents/" + docId).header("Authorization", "Bearer " + doctorBToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/documents/" + docId).header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Firmar un documento genera hash SHA-256 real")
	void signingGeneratesRealHash() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/documents")
				.header("Authorization", "Bearer " + doctorAToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Para firmar\",\"type\":\"consentimiento_informado\",\"initialContent\":\"contenido\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		String docId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(put("/api/documents/" + docId)
				.header("Authorization", "Bearer " + doctorAToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"firmado\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("firmado"))
				.andExpect(jsonPath("$.data.signatures[0].type").value("digital"))
				.andExpect(jsonPath("$.data.signatures[0].isValid").value(true))
				.andExpect(jsonPath("$.data.signatures[0].hash")
						.value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")));
	}

	// ── Módulos solo-admin y perfil ─────────────────────────────────────────

	@Test
	@DisplayName("users y audit son solo de admin (doctor → 403, admin → 200)")
	void usersAndAuditAreAdminOnly() throws Exception {
		mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + doctorAToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/audit").header("Authorization", "Bearer " + doctorAToken))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/audit").header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Un usuario NO puede leer el perfil de otro; admin sí")
	void profileOwnership() throws Exception {
		mockMvc.perform(get("/api/profile").param("userId", doctorAId)
				.header("Authorization", "Bearer " + doctorBToken))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/profile").param("userId", doctorAId)
				.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.professional.specialty").value("Cirugía General"));
	}

	// ── Helpers ─────────────────────────────────────────────────────────────

	private void registerUser(String role, String email, String extraJson) throws Exception {
		String base = "{\"name\":\"Test User\",\"email\":\"" + email
				+ "\",\"password\":\"Password123\",\"role\":\"" + role + "\"";
		String body = base + "," + extraJson.substring(1);
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
	}

	private String loginDemo(String role) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"" + role + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
	}

	private String loginEmail(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
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
				.content("{\"title\":\"" + title + "\",\"description\":\"Descripción de prueba\","
						+ "\"priority\":\"media\",\"context\":{\"medicalArea\":\"Cirugía General\","
						+ "\"ageReference\":45,\"summary\":\"Contexto simulado\"}}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.context.isSimulated").value(true))
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
	}
}
