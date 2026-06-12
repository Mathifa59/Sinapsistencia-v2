package pe.sinapsistencia.profile.web.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Body de PATCH /api/profile: {userId?, name?, professional?}.
 * Acepta los alias snake_case que enviaba el frontend legacy.
 */
public record UpdateProfileRequest(String userId, String name, ProfessionalPayload professional) {

	public record ProfessionalPayload(
			String cmp,
			String specialty,
			String hospital,
			String cab,
			List<String> specialties,
			@JsonAlias("medical_areas") List<String> medicalAreas,
			@JsonAlias("years_experience") Integer yearsExperience,
			Boolean available,
			String phone,
			String bio) {
	}
}
