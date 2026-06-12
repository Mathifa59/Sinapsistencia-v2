package pe.sinapsistencia.profile.web.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import pe.sinapsistencia.profile.domain.DoctorProfile;
import pe.sinapsistencia.profile.domain.LawyerProfile;

/**
 * Perfil profesional (unión doctor/lawyer; los campos del otro rol van null y
 * se omiten por NON_NULL). El legacy devolvía la fila cruda de Supabase;
 * aquí se normaliza a camelCase para el cliente generado por OpenAPI.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfessionalDto(
		// doctor
		String cmp,
		String specialty,
		List<String> subSpecialties,
		String hospital,
		List<String> languages,
		// lawyer
		String cab,
		List<String> specialties,
		List<String> medicalAreas,
		BigDecimal rating,
		Integer resolvedCases,
		Boolean available,
		// comunes
		Integer yearsExperience,
		String phone,
		String bio) {

	public static ProfessionalDto from(DoctorProfile d) {
		return new ProfessionalDto(
				d.getCmp(), d.getSpecialty(), d.getSubSpecialties(), d.getHospital(), d.getLanguages(),
				null, null, null, null, null, null,
				d.getYearsExperience(), d.getPhone(), d.getBio());
	}

	public static ProfessionalDto from(LawyerProfile l) {
		return new ProfessionalDto(
				null, null, null, null, null,
				l.getCab(), l.getSpecialties(), l.getMedicalAreas(), l.getRating(), l.getResolvedCases(),
				l.isAvailable(),
				l.getYearsExperience(), l.getPhone(), l.getBio());
	}
}
