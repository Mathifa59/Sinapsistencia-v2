package pe.sinapsistencia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cloudinary.Cloudinary;

/**
 * Cliente Cloudinary (storage de avatares y documentos de soporte).
 * Solo se crea si CLOUDINARY_URL está configurado (no vacío); sin él, los
 * endpoints de upload responden 503 y la app sigue funcionando.
 */
@Configuration
public class CloudinaryConfig {

	@Bean
	@ConditionalOnExpression("'${app.cloudinary.url:}' != ''")
	public Cloudinary cloudinary(@Value("${app.cloudinary.url}") String cloudinaryUrl) {
		return new Cloudinary(cloudinaryUrl);
	}
}
