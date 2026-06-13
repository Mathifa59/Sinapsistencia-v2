package pe.sinapsistencia.matching.application;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.sinapsistencia.auth.domain.UserRole;
import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.cases.domain.CaseContext;
import pe.sinapsistencia.cases.domain.LegalCase;
import pe.sinapsistencia.cases.infrastructure.CaseContextRepository;
import pe.sinapsistencia.cases.infrastructure.LegalCaseRepository;
import pe.sinapsistencia.profile.domain.DoctorProfile;
import pe.sinapsistencia.profile.domain.LawyerProfile;
import pe.sinapsistencia.profile.infrastructure.DoctorProfileRepository;
import pe.sinapsistencia.profile.infrastructure.LawyerProfileRepository;
import pe.sinapsistencia.shared.exception.BadRequestException;
import pe.sinapsistencia.shared.exception.ForbiddenException;
import pe.sinapsistencia.shared.exception.NotFoundException;

/** Casos relevantes para el abogado: consultas pendientes que coinciden con sus áreas médicas. */
@Service
public class RelevantCasesService {

	private final LawyerProfileRepository lawyerProfileRepository;
	private final DoctorProfileRepository doctorProfileRepository;
	private final LegalCaseRepository caseRepository;
	private final CaseContextRepository contextRepository;

	public RelevantCasesService(LawyerProfileRepository lawyerProfileRepository,
			DoctorProfileRepository doctorProfileRepository,
			LegalCaseRepository caseRepository,
			CaseContextRepository contextRepository) {
		this.lawyerProfileRepository = lawyerProfileRepository;
		this.doctorProfileRepository = doctorProfileRepository;
		this.caseRepository = caseRepository;
		this.contextRepository = contextRepository;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> relevantCases(AuthenticatedUser user, String lawyerIdParam) {
		if (lawyerIdParam == null || lawyerIdParam.isBlank()) {
			throw new BadRequestException("lawyerId es requerido");
		}
		UUID lawyerId = UUID.fromString(lawyerIdParam);
		if (user.role() != UserRole.ADMIN && !lawyerId.equals(user.id())) {
			throw new ForbiddenException("No tienes permisos para ver casos de otro abogado");
		}

		LawyerProfile lawyerProfile = lawyerProfileRepository.findByUserId(lawyerId)
				.orElseThrow(() -> new NotFoundException("Perfil de abogado no encontrado"));

		List<String> medicalAreas = lawyerProfile.getMedicalAreas();
		if (medicalAreas.isEmpty()) {
			return Map.of("data", List.of(), "medicalAreas", List.of());
		}

		List<DoctorProfile> matchingDoctors = doctorProfileRepository.findBySpecialtyIn(medicalAreas);
		if (matchingDoctors.isEmpty()) {
			return Map.of("data", List.of(), "medicalAreas", medicalAreas);
		}

		Map<UUID, String> specialtyByDoctor = matchingDoctors.stream()
				.collect(Collectors.toMap(d -> d.getUser().getId(), DoctorProfile::getSpecialty));

		List<LegalCase> cases = caseRepository.findRelevantUnassigned(
				specialtyByDoctor.keySet(), PageRequest.of(0, 20));

		Map<UUID, CaseContext> contexts = cases.isEmpty() ? Map.of()
				: contextRepository.findByLegalCaseIdIn(cases.stream().map(LegalCase::getId).toList()).stream()
						.collect(Collectors.toMap(c -> c.getLegalCase().getId(), Function.identity()));

		List<Map<String, Object>> enriched = cases.stream().map(c -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("id", c.getId().toString());
			item.put("title", c.getTitle());
			item.put("description", c.getDescription());
			item.put("status", c.getStatus().getValue());
			item.put("priority", c.getPriority().getValue());
			item.put("doctorId", c.getDoctor().getId().toString());
			item.put("doctor", Map.of(
					"id", c.getDoctor().getId().toString(),
					"fullName", c.getDoctor().getName(),
					"specialty", specialtyByDoctor.getOrDefault(c.getDoctor().getId(), "No especificada")));
			var context = contexts.get(c.getId());
			if (context != null) {
				item.put("context", Map.of(
						"contextCode", context.getContextCode(),
						"medicalArea", context.getMedicalArea(),
						"isSimulated", context.isSimulated()));
			}
			item.put("notes", c.getNotes());
			item.put("createdAt", c.getCreatedAt());
			return item;
		}).toList();

		Map<String, Object> result = new HashMap<>();
		result.put("data", enriched);
		result.put("medicalAreas", medicalAreas);
		return result;
	}
}
