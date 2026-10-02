package pe.sinapsistencia.notifications;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Falla el arranque si falta {@code RESEND_API_KEY}, salvo en los perfiles
 * {@code local} y {@code test}. Sin esta llave no hay forma de entregar el token de
 * recuperación de contraseña ni los avisos del outbox, y arrancar igual dejaría un
 * entorno que parece sano pero no puede entregar nada.
 *
 * <p>Se valida en la construcción del bean, antes de que el servidor web empiece a
 * atender: un despliegue sin la variable no llega a recibir tráfico.
 */
@Component
@Profile("!local & !test")
public class ResendStartupValidator {

	public ResendStartupValidator(ResendClient resendClient) {
		if (!resendClient.isConfigured()) {
			throw new IllegalStateException(
					"RESEND_API_KEY no está configurada o está vacía: la aplicación no arranca sin proveedor de correo. "
							+ "Define la variable de entorno o, solo en desarrollo, activa el perfil 'local' "
							+ "(SPRING_PROFILES_ACTIVE=local).");
		}
	}
}
