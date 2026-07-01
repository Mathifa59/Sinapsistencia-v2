package pe.sinapsistencia.auth.web.dto;

/** Respuesta de recuperación — incluye token para entorno académico sin SMTP. */
public record ForgotPasswordResponse(String message, String resetToken) {
}
