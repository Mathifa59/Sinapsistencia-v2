package pe.sinapsistencia.auth.web.dto;

public record LoginResponse(UserDto user, String token) {
}
