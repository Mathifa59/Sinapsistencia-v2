package pe.sinapsistencia.auth.web.dto;

import java.time.Instant;

import pe.sinapsistencia.auth.domain.Profile;

/** Shape del usuario en las respuestas de auth — idéntico al BFF legacy. */
public record UserDto(
		String id,
		String email,
		String name,
		String role,
		boolean isActive,
		Instant createdAt,
		String avatar) {

	public static UserDto from(Profile profile) {
		return new UserDto(
				profile.getId().toString(),
				profile.getEmail(),
				profile.getName(),
				profile.getRole().getValue(),
				profile.isActive(),
				profile.getCreatedAt(),
				profile.getAvatarUrl());
	}
}
