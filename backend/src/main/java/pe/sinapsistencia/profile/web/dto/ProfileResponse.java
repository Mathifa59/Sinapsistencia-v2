package pe.sinapsistencia.profile.web.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import pe.sinapsistencia.auth.domain.Profile;

/** GET /api/profile — perfil base + profesional (igual que el legacy). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileResponse(
		String id,
		String email,
		String name,
		String role,
		String avatar,
		boolean isActive,
		Instant createdAt,
		ProfessionalDto professional) {

	public static ProfileResponse from(Profile profile, ProfessionalDto professional) {
		return new ProfileResponse(
				profile.getId().toString(),
				profile.getEmail(),
				profile.getName(),
				profile.getRole().getValue(),
				profile.getAvatarUrl(),
				profile.isActive(),
				profile.getCreatedAt(),
				professional);
	}
}
