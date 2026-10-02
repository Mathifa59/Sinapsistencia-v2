package pe.sinapsistencia.auth.web.dto;

/** Body de POST /api/auth/login: siempre email + password. */
public record LoginRequest(String email, String password) {
}
