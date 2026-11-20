package pe.sinapsistencia.auth.web.dto;

/** Confirmación del correo con el token recibido por correo. */
public record VerifyEmailRequest(String email, String token) {
}
