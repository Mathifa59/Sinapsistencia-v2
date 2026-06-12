package pe.sinapsistencia.matching.web.dto;

import java.util.List;

import pe.sinapsistencia.profile.domain.LawyerProfile;

/** Tarjeta de abogado del directorio/matching — shape exacto del legacy. */
public record LawyerCardDto(
		String id,
		String userId,
		String fullName,
		String email,
		String cab,
		List<String> specialties,
		List<String> medicalAreas,
		int yearsExperience,
		int resolvedCases,
		double rating,
		String phone,
		String bio,
		boolean available,
		String avatar) {

	public static LawyerCardDto from(LawyerProfile l) {
		return new LawyerCardDto(
				l.getId().toString(),
				l.getUser().getId().toString(),
				l.getUser().getName(),
				l.getUser().getEmail(),
				l.getCab(),
				l.getSpecialties(),
				l.getMedicalAreas(),
				l.getYearsExperience(),
				l.getResolvedCases(),
				l.getRating().doubleValue(),
				l.getPhone(),
				l.getBio(),
				l.isAvailable() && l.getUser().isActive(),
				l.getUser().getAvatarUrl());
	}
}
