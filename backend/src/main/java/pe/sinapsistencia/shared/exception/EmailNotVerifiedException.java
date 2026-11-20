package pe.sinapsistencia.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Credenciales correctas pero correo sin confirmar (403). El frontend reconoce
 * este caso por {@link #MESSAGE} para ofrecer el reenvío del enlace.
 */
public class EmailNotVerifiedException extends ApiException {

	/** Texto estable: el frontend lo compara; cámbialo en ambos lados a la vez. */
	public static final String MESSAGE =
			"Debes confirmar tu correo electrónico antes de iniciar sesión. Revisa tu bandeja o solicita un nuevo enlace.";

	public EmailNotVerifiedException() {
		super(HttpStatus.FORBIDDEN, MESSAGE);
	}
}
