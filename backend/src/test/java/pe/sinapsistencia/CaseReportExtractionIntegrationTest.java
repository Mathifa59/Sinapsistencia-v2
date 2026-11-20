package pe.sinapsistencia;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import pe.sinapsistencia.ml.application.MlProxyService;
import pe.sinapsistencia.shared.exception.ServiceUnavailableException;

/**
 * POST /api/legal-cases/extract-from-report: solo médicos, valida la imagen antes de
 * llamar al servicio ML y degrada con un mensaje claro si el ML no está disponible.
 */
@SpringBootTest(properties = {
		"spring.docker.compose.enabled=false",
		"app.notifications.worker-interval-ms=600000"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CaseReportExtractionIntegrationTest {

	private static final String URL = "/api/legal-cases/extract-from-report";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MlProxyService mlProxyService;

	@Test
	@DisplayName("Un médico recibe la propuesta de campos con su origen (detectado/inferido/ausente)")
	void medicoRecibeLaPropuesta() throws Exception {
		Map<String, Object> campos = new LinkedHashMap<>();
		campos.put("medicalSpecialty", Map.of("value", "Cirugía General", "source", "detected"));
		campos.put("informedConsent", new LinkedHashMap<String, Object>(Map.of("source", "missing")));
		Map<String, Object> propuesta = new LinkedHashMap<>();
		propuesta.put("extractorVersion", "extract-v1");
		propuesta.put("fields", campos);
		propuesta.put("piiRedacted", List.of("DNI"));
		propuesta.put("advisory", "revisión humana");
		when(mlProxyService.extractCaseReport(any(), any(), eq("image/png"))).thenReturn(propuesta);

		mockMvc.perform(archivo("image/png", new byte[] { 1, 2, 3 }, login("doctor")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.extractorVersion").value("extract-v1"))
				.andExpect(jsonPath("$.data.fields.medicalSpecialty.value").value("Cirugía General"))
				.andExpect(jsonPath("$.data.fields.informedConsent.source").value("missing"))
				.andExpect(jsonPath("$.data.piiRedacted[0]").value("DNI"));
	}

	@Test
	@DisplayName("Un abogado no puede usar la extracción (403) y no se llama al ML")
	void abogadoNoPuede() throws Exception {
		mockMvc.perform(archivo("image/png", new byte[] { 1 }, login("lawyer")))
				.andExpect(status().isForbidden());
		verify(mlProxyService, never()).extractCaseReport(any(), any(), any());
	}

	@Test
	@DisplayName("Sin sesión responde 401")
	void sinSesion() throws Exception {
		mockMvc.perform(multipart(URL).file(new MockMultipartFile("file", "i.png", "image/png", new byte[] { 1 })))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Rechaza formatos que no son imagen antes de llamar al ML")
	void rechazaFormatoInvalido() throws Exception {
		mockMvc.perform(archivo("application/pdf", new byte[] { 1 }, login("doctor")))
				.andExpect(status().isBadRequest());
		verify(mlProxyService, never()).extractCaseReport(any(), any(), any());
	}

	@Test
	@DisplayName("Rechaza un archivo vacío")
	void rechazaVacio() throws Exception {
		mockMvc.perform(archivo("image/png", new byte[0], login("doctor")))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Si el ML no está disponible, 503 con instrucción de completar el formulario a mano")
	void degradaCuandoElMlNoResponde() throws Exception {
		when(mlProxyService.extractCaseReport(any(), any(), any()))
				.thenThrow(new ServiceUnavailableException(
						"La lectura automática de informes no está disponible. Completa el formulario manualmente."));

		mockMvc.perform(archivo("image/png", new byte[] { 1, 2 }, login("doctor")))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("formulario manualmente")));
	}

	// ── utilidades ────────────────────────────────────────────────────────────

	private MockMultipartHttpServletRequestBuilder archivo(String tipo, byte[] contenido, String token) {
		return (MockMultipartHttpServletRequestBuilder) multipart(URL)
				.file(new MockMultipartFile("file", "informe.png", tipo, contenido))
				.header("Authorization", "Bearer " + token);
	}

	private String login(String role) throws Exception {
		MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + role + ".demo@sinapsistencia.pe\",\"password\":\"Demo123!\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
	}
}
