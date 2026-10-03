package pe.sinapsistencia.auth.web.dto;

/** Respuesta de recuperación: solo el mensaje; el token viaja únicamente por correo. */
public record ForgotPasswordResponse(String message) {
}
