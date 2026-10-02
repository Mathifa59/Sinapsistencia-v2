package pe.sinapsistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import pe.sinapsistencia.notifications.ResendClient;

/**
 * Perfil {@code local} real (carga application-local.yml) sin Resend: el token de
 * recuperación se imprime en el log del servidor para el desarrollador, pero nunca
 * viaja en la respuesta HTTP.
 */
@SpringBootTest(properties = {
		"spring.docker.compose.enabled=false",
		"app.notifications.worker-interval-ms=600000"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("local")
@ExtendWith(OutputCaptureExtension.class)
class ForgotPasswordLocalProfileIntegrationTest {

	private static final String EMAIL = "doctor.demo@sinapsistencia.pe";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private ResendClient resendClient;

	@Test
	@DisplayName("Perfil local sin Resend: el token va al log del servidor, nunca a la respuesta")
	void perfilLocalImprimeElTokenEnElLogYNoEnLaRespuesta(CapturedOutput output) throws Exception {
		when(resendClient.isConfigured()).thenReturn(false);
		Set<String> before = tokens();

		MvcResult result = mockMvc.perform(post("/api/auth/forgot-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + EMAIL + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.resetToken").doesNotExist())
				.andReturn();

		Set<String> created = tokens();
		created.removeAll(before);
		assertThat(created).hasSize(1);
		String token = created.iterator().next();

		assertThat(output.getAll()).contains(token);
		assertThat(result.getResponse().getContentAsString()).doesNotContain(token);
	}

	private Set<String> tokens() {
		List<String> rows = jdbc.queryForList("select token from password_reset_tokens where email = ?", String.class, EMAIL);
		return new HashSet<>(rows);
	}
}
