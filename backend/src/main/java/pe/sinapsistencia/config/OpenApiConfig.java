package pe.sinapsistencia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/** OpenAPI/Swagger; de aquí se genera el cliente TypeScript del frontend (FASE 6). */
@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI sinapsistenciaOpenApi() {
		return new OpenAPI().info(new Info()
				.title("Sinapsistencia API")
				.description("Plataforma de mediación médico-legal — backend Spring Boot. "
						+ "Contrato de respuesta: {success, data} / {success, error}.")
				.version("v1"));
	}
}
