package pe.sinapsistencia.matching.web.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import pe.sinapsistencia.matching.domain.ContactRequest;
import pe.sinapsistencia.profile.domain.DoctorProfile;
import pe.sinapsistencia.profile.domain.LawyerProfile;

/** Solicitud de contacto — shape del legacy con snapshots enriquecidos por rol. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContactRequestResponse(
		String id,
		String fromDoctorId,
		DoctorSnapshot fromDoctor,
		String toLawyerId,
		LawyerSnapshot toLawyer,
		String caseId,
		String caseTitle,
		String status,
		String message,
		String responseMessage,
		Double mlScore,
		Instant createdAt,
		Instant respondedAt) {

	public record DoctorSnapshot(String id, String userId, String fullName, String email,
			String cmp, String specialty, String hospital, String phone) {
	}

	public record LawyerSnapshot(String id, String userId, String fullName, String email,
			String cab, List<String> specialties, String phone, double rating) {
	}

	public static ContactRequestResponse from(ContactRequest r, DoctorProfile doctorProfile,
			LawyerProfile lawyerProfile) {
		var doctorUser = r.getFromDoctor();
		var lawyerUser = r.getToLawyer();

		DoctorSnapshot doctor = new DoctorSnapshot(
				doctorProfile != null ? doctorProfile.getId().toString() : doctorUser.getId().toString(),
				doctorUser.getId().toString(),
				doctorUser.getName(),
				doctorUser.getEmail(),
				doctorProfile != null ? doctorProfile.getCmp() : "",
				doctorProfile != null ? doctorProfile.getSpecialty() : "",
				doctorProfile != null ? doctorProfile.getHospital() : "",
				doctorProfile != null ? doctorProfile.getPhone() : "");

		LawyerSnapshot lawyer = new LawyerSnapshot(
				lawyerProfile != null ? lawyerProfile.getId().toString() : lawyerUser.getId().toString(),
				lawyerUser.getId().toString(),
				lawyerUser.getName(),
				lawyerUser.getEmail(),
				lawyerProfile != null ? lawyerProfile.getCab() : "",
				lawyerProfile != null ? lawyerProfile.getSpecialties() : List.of(),
				lawyerProfile != null ? lawyerProfile.getPhone() : "",
				lawyerProfile != null ? lawyerProfile.getRating().doubleValue() : 0);

		return new ContactRequestResponse(
				r.getId().toString(),
				doctorUser.getId().toString(),
				doctor,
				lawyerUser.getId().toString(),
				lawyer,
				r.getLegalCase() == null ? null : r.getLegalCase().getId().toString(),
				r.getCaseTitle(),
				r.getStatus().getValue(),
				r.getMessage(),
				r.getResponseMessage(),
				r.getMlScore() == null ? null : r.getMlScore().doubleValue(),
				r.getCreatedAt(),
				// updatedAt puede ser null justo tras el INSERT (antes del flush)
				r.getUpdatedAt() == null || r.getUpdatedAt().equals(r.getCreatedAt())
						? null : r.getUpdatedAt());
	}
}
