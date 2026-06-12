package pe.sinapsistencia.shared.exception;

import org.springframework.http.HttpStatus;

/** Base de las excepciones de negocio; el GlobalExceptionHandler las convierte a {success:false, error}. */
public abstract class ApiException extends RuntimeException {

	private final HttpStatus status;

	protected ApiException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
