package pe.sinapsistencia.matching.web.dto;

import java.util.List;

import pe.sinapsistencia.profile.domain.DoctorProfile;

/** Tarjeta de médico del directorio — shape exacto del legacy. */
public record DoctorCardDto(
		String id,
		String userId,
		String fullName,
		String email,
		String cmp,
		String specialty,
		List<String> subSpecialties,
		String hospital,
		int yearsExperience,
		String phone,
		String bio,
		List<String> languages,
		boolean available,
		String avatar) {

	public static DoctorCardDto from(DoctorProfile d) {
		return new DoctorCardDto(
				d.getId().toString(),
				d.getUser().getId().toString(),
				d.getUser().getName(),
				d.getUser().getEmail(),
				d.getCmp(),
				d.getSpecialty(),
				d.getSubSpecialties(),
				d.getHospital(),
				d.getYearsExperience(),
				d.getPhone(),
				d.getBio(),
				d.getLanguages(),
				d.getUser().isActive(),
				d.getUser().getAvatarUrl());
	}
}
