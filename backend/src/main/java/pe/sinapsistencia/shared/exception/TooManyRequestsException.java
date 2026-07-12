package pe.sinapsistencia.shared.exception;

import org.springframework.http.HttpStatus;

/** 429 — límite de intentos superado (protección de fuerza bruta). */
public class TooManyRequestsException extends ApiException {

	public TooManyRequestsException(String message) {
		super(HttpStatus.TOO_MANY_REQUESTS, message);
	}
}
