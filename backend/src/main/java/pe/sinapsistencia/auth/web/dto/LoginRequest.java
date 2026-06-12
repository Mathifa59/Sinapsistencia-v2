package pe.sinapsistencia.auth.web.dto;

/**
 * Body de POST /api/auth/login. Dos modos (igual que el legacy):
 * email + password, o {@code role} para acceso demo (doctor/lawyer/admin).
 */
public record LoginRequest(String email, String password, String role) {
}
