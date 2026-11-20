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

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pe.sinapsistencia.notifications.ResendClient;

/**
 * V18: el correo debe confirmarse antes de iniciar sesión y la recuperación de contraseña
 * solo se entrega a un correo confirmado. El token viaja únicamente en el correo.
 */
@SpringBootTest(properties = {
		"spring.docker.compose.enabled=false",
		"app.notifications.worker-interval-ms=600000"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EmailVerificationIntegrationTest {

	private static final String PASSWORD = "ClaveSegura123";
	private static final Pattern TOKEN_IN_LINK = Pattern.compile("verify-email\\?email=[^&\"]+&amp;token=([0-9a-f]+)|verify-email\\?email=[^&\"]+&token=([0-9a-f]+)");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private ResendClient resendClient;

	@Test
	@DisplayName("Registro: la cuenta nace sin confirmar y no inicia sesión hasta confirmar el correo")
	void registroExigeConfirmarElCorreo() throws Exception {
		when(resendClient.isConfigured()).thenReturn(true);
		String email = nuevoCorreo();

		registrar(email);

		assertThat(jdbc.queryForObject(
				"select email_verified_at is null from profiles where email = ?", Boolean.class, email)).isTrue();
		login(email, PASSWORD)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("confirmar tu correo")));

		String token = tokenEnviadoA(email);
		// En BD solo vive el hash, nunca el token en claro.
		assertThat(jdbc.queryForObject(
				"select count(*) from email_verification_tokens where token_hash = ?", Integer.class, token)).isZero();

		confirmar(email, token).andExpect(status().isOk());
		login(email, PASSWORD).andExpect(status().isOk());

		// Un solo uso.
		confirmar(email, token).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("El token de confirmación no sirve para otro correo")
	void tokenAtadoAlCorreo() throws Exception {
		when(resendClient.isConfigured()).thenReturn(true);
		String email = nuevoCorreo();
		registrar(email);
		String token = tokenEnviadoA(email);

		confirmar("otra.persona@example.com", token).andExpect(status().isBadRequest());
		login(email, PASSWORD).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Recuperar contraseña con el correo sin confirmar no emite token de recuperación")
	void recuperacionSoloParaCorreoConfirmado() throws Exception {
		when(resendClient.isConfigured()).thenReturn(true);
		String email = nuevoCorreo();
		registrar(email);

		mockMvc.perform(post("/api/auth/forgot-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isOk());

		assertThat(jdbc.queryForObject(
				"select count(*) from password_reset_tokens where email = ?", Integer.class, email)).isZero();

		// Tras confirmar, la recuperación sí emite el token y queda atado al perfil.
		confirmar(email, tokenEnviadoA(email)).andExpect(status().isOk());
		mockMvc.perform(post("/api/auth/forgot-password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isOk());

		UUID profileId = jdbc.queryForObject("select id from profiles where email = ?", UUID.class, email);
		assertThat(jdbc.queryForObject(
				"select count(*) from password_reset_tokens where email = ? and profile_id = ?",
				Integer.class, email, profileId)).isEqualTo(1);
	}

	@Test
	@DisplayName("Reenviar la confirmación responde igual exista o no la cuenta")
	void reenvioNoRevelaCuentas() throws Exception {
		when(resendClient.isConfigured()).thenReturn(true);

		mockMvc.perform(post("/api/auth/resend-verification")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + nuevoCorreo() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.message").isNotEmpty());
	}

	@Test
	@DisplayName("Las cuentas anteriores a V18 (semillas demo) quedan confirmadas")
	void cuentasExistentesQuedanConfirmadas() {
		assertThat(jdbc.queryForObject(
				"select count(*) from profiles where email_verified_at is null and email like '%.demo@sinapsistencia.pe'",
				Integer.class)).isZero();
	}

	// ── utilidades ────────────────────────────────────────────────────────────

	private String nuevoCorreo() {
		return "prueba." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
	}

	private void registrar(String email) throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Dr. Prueba Simulado","email":"%s","password":"%s","role":"doctor",
						 "specialty":"Cirugía General","acceptPrivacyPolicy":true}
						""".formatted(email, PASSWORD)))
				.andExpect(status().isCreated());
	}

	private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
	}

	private org.springframework.test.web.servlet.ResultActions confirmar(String email, String token) throws Exception {
		return mockMvc.perform(post("/api/auth/verify-email")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"token\":\"" + token + "\"}"));
	}

	/** Extrae el token del enlace del correo de confirmación enviado a {@code email}. */
	private String tokenEnviadoA(String email) {
		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		verify(resendClient, timeout(5000).atLeastOnce()).send(eq(email), anyString(), html.capture(), isNull());
		for (int i = html.getAllValues().size() - 1; i >= 0; i--) {
			Matcher m = TOKEN_IN_LINK.matcher(html.getAllValues().get(i));
			if (m.find()) {
				return m.group(1) != null ? m.group(1) : m.group(2);
			}
		}
		throw new AssertionError("No se encontró un enlace de confirmación en los correos enviados a " + email);
	}
}
