package pe.sinapsistencia.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * La app no debe arrancar sin RESEND_API_KEY, salvo en los perfiles local y test.
 * Sin esa llave no hay forma de entregar el token de recuperación ni los avisos.
 */
class ResendStartupValidatorTest {

	private ApplicationContextRunner runner(String... profiles) {
		return new ApplicationContextRunner()
				.withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles))
				.withUserConfiguration(ResendClient.class, ResendStartupValidator.class)
				.withPropertyValues("app.resend.from=notificaciones@sinapsistencia.com");
	}

	@Test
	@DisplayName("Sin perfil y sin RESEND_API_KEY: la aplicación no arranca")
	void sinLlaveNoArranca() {
		runner().withPropertyValues("app.resend.api-key=").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("RESEND_API_KEY");
		});
	}

	@Test
	@DisplayName("Una llave en blanco cuenta como ausente")
	void llaveEnBlancoNoArranca() {
		runner().withPropertyValues("app.resend.api-key=   ").run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("Sin la propiedad definida tampoco arranca")
	void sinPropiedadNoArranca() {
		runner().run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("Un perfil de producción sin llave tampoco arranca")
	void perfilProdSinLlaveNoArranca() {
		runner("prod").withPropertyValues("app.resend.api-key=").run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("Con RESEND_API_KEY configurada arranca")
	void conLlaveArranca() {
		runner().withPropertyValues("app.resend.api-key=re_clave_de_prueba").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(ResendStartupValidator.class);
		});
	}

	@Test
	@DisplayName("Perfil local arranca sin llave (y la validación no se aplica)")
	void perfilLocalArrancaSinLlave() {
		runner("local").withPropertyValues("app.resend.api-key=").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(ResendStartupValidator.class);
		});
	}

	@Test
	@DisplayName("Perfil test arranca sin llave (y la validación no se aplica)")
	void perfilTestArrancaSinLlave() {
		runner("test").withPropertyValues("app.resend.api-key=").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(ResendStartupValidator.class);
		});
	}
}
