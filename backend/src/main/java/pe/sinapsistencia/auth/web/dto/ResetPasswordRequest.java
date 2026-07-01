package pe.sinapsistencia.auth.web.dto;

/** Restablecimiento con token (HU-04). */
public record ResetPasswordRequest(String email, String token, String newPassword) {
}
