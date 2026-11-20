package pe.sinapsistencia.auth.web.dto;

/** Solicitud de un nuevo enlace de confirmación de correo. */
public record ResendVerificationRequest(String email) {
}
