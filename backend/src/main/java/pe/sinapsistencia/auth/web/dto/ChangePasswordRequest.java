package pe.sinapsistencia.auth.web.dto;

/** Cambio de contraseña autenticado (HU-04). */
public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
