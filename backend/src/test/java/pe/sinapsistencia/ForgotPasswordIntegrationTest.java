package pe.sinapsistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import pe.sinapsistencia.notifications.ResendClient;

/**
 * Recuperación de contraseña con el perfil {@code test} (sin {@code app.auth.log-reset-token}):
 * el token nunca debe aparecer en la respuesta HTTP -- ni con Resend configurado
 * (viaja solo en el correo) ni sin él (no se imprime en el log fuera del perfil local).
 */
@SpringBootTest(properties = {
		"spring.docker.compose.enabled=false",
		"app.notifications.worker-interval-ms=600000"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ForgotPasswordIntegrationTest {

	private static final String EMAIL = "doctor.demo@sinapsistencia.pe";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private ResendClient resendClient;

	@Test
	@DisplayName("Sin Resend: la respuesta no trae el token y fuera del perfil local tampoco se imprime en el log")
	void sinResendNiRespuestaNiLogLlevanElToken(CapturedOutput output) throws Exception {
		when(resendClient.isConfigured()).thenReturn(false);
		Set<String> before = tokens();

		MvcResult result = solicitarRecuperacion();

		String token = nuevoToken(before);
		assertThat(result.getResponse().getContentAsString()).doesNotContain(token);
		assertThat(output.getAll()).doesNotContain(token);
	}

	@Test
	@DisplayName("Con Resend: la respuesta no trae el token; viaja solo en el correo")
	void conResendElTokenSoloVaEnElCorreo() throws Exception {
		when(resendClient.isConfigured()).thenReturn(true);
		Set<String> before = tokens();

		MvcResult result = solicitarRecuperacion();

		String token = nuevoToken(before);
		assertThat(result.getResponse().getContentAsString()).doesNotContain(token);

		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		verify(resendClient, timeout(5000)).send(eq(EMAIL), anyString(), html.capture(), isNull());
		assertThat(html.getValue()).contains(token);
	}

	private MvcResult solicitarRecuperacion() throws Exception {
		return mockMvc.perform(post("/api/auth/forgot-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + EMAIL + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.message").isNotEmpty())
				.andExpect(jsonPath("$.data.resetToken").doesNotExist())
				.andReturn();
	}

	private Set<String> tokens() {
		List<String> rows = jdbc.queryForList("select token from password_reset_tokens where email = ?", String.class, EMAIL);
		return new HashSet<>(rows);
	}

	private String nuevoToken(Set<String> before) {
		Set<String> created = tokens();
		created.removeAll(before);
		assertThat(created).hasSize(1);
		return created.iterator().next();
	}
}
