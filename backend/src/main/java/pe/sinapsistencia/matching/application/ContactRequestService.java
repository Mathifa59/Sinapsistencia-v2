package pe.sinapsistencia.matching.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.sinapsistencia.auth.domain.UserRole;
import pe.sinapsistencia.auth.infrastructure.ProfileRepository;
import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.cases.domain.CaseStatus;
import pe.sinapsistencia.cases.domain.LegalCase;
import pe.sinapsistencia.cases.infrastructure.LegalCaseRepository;
import pe.sinapsistencia.matching.domain.ContactRequest;
import pe.sinapsistencia.matching.domain.ContactRequestStatus;
import pe.sinapsistencia.matching.infrastructure.ContactRequestRepository;
import pe.sinapsistencia.matching.web.dto.ContactRequestResponse;
import pe.sinapsistencia.profile.domain.DoctorProfile;
import pe.sinapsistencia.profile.domain.LawyerProfile;
import pe.sinapsistencia.profile.infrastructure.DoctorProfileRepository;
import pe.sinapsistencia.profile.infrastructure.LawyerProfileRepository;
import pe.sinapsistencia.shared.exception.BadRequestException;
import pe.sinapsistencia.shared.exception.ConflictException;
import pe.sinapsistencia.shared.exception.ForbiddenException;
import pe.sinapsistencia.shared.exception.NotFoundException;

/** Solicitudes de contacto médico→abogado: listar, crear y responder (HU-16/18). */
@Service
public class ContactRequestService {

	private final ContactRequestRepository contactRequestRepository;
	private final ProfileRepository profileRepository;
	private final LegalCaseRepository caseRepository;
	private final DoctorProfileRepository doctorProfileRepository;
	private final LawyerProfileRepository lawyerProfileRepository;

	public ContactRequestService(ContactRequestRepository contactRequestRepository,
			ProfileRepository profileRepository,
			LegalCaseRepository caseRepository,
			DoctorProfileRepository doctorProfileRepository,
			LawyerProfileRepository lawyerProfileRepository) {
		this.contactRequestRepository = contactRequestRepository;
		this.profileRepository = profileRepository;
		this.caseRepository = caseRepository;
		this.doctorProfileRepository = doctorProfileRepository;
		this.lawyerProfileRepository = lawyerProfileRepository;
	}

	@Transactional(readOnly = true)
	public List<ContactRequestResponse> listContactRequests(AuthenticatedUser user,
			String lawyerIdParam, String doctorIdParam, String status) {

		// Ownership: doctor ve las que envió; lawyer las que recibió; admin filtra libre
		Specification<ContactRequest> spec = switch (user.role()) {
			case DOCTOR -> (root, q, cb) -> cb.equal(root.get("fromDoctor").get("id"), user.id());
			case LAWYER -> (root, q, cb) -> cb.equal(root.get("toLawyer").get("id"), user.id());
			case ADMIN -> (root, q, cb) -> cb.conjunction();
		};

		if (user.role() == UserRole.ADMIN) {
			if (lawyerIdParam != null) {
				UUID lawyerId = UUID.fromString(lawyerIdParam);
				spec = spec.and((root, q, cb) -> cb.equal(root.get("toLawyer").get("id"), lawyerId));
			}
			if (doctorIdParam != null) {
				UUID doctorId = UUID.fromString(doctorIdParam);
				spec = spec.and((root, q, cb) -> cb.equal(root.get("fromDoctor").get("id"), doctorId));
			}
		}
		if (status != null) {
			ContactRequestStatus statusEnum = parseStatus(status);
			spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), statusEnum));
		}

		List<ContactRequest> requests = contactRequestRepository.findAll(spec,
				Sort.by(Sort.Direction.DESC, "createdAt"));

		return enrich(requests);
	}

	@Transactional
	public ContactRequestResponse createContactRequest(AuthenticatedUser user, String toLawyerIdParam,
			String message, String caseIdParam) {
		if (user.role() != UserRole.DOCTOR) {
			throw new ForbiddenException("Solo un médico puede enviar solicitudes de contacto");
		}
		if (toLawyerIdParam == null || message == null || message.isBlank()) {
			throw new BadRequestException("Doctor, abogado y mensaje son requeridos");
		}

		UUID toLawyerId = UUID.fromString(toLawyerIdParam);
		var doctor = profileRepository.findById(user.id())
				.orElseThrow(() -> new NotFoundException("Perfil no encontrado"));
		var lawyer = profileRepository.findById(toLawyerId)
				.orElseThrow(() -> new NotFoundException("Abogado no encontrado"));
		if (lawyer.getRole() != UserRole.LAWYER) {
			throw new BadRequestException("El destinatario debe ser un abogado");
		}

		// HU-16 legacy: sin solicitudes duplicadas pendientes al mismo abogado
		if (contactRequestRepository.existsByFromDoctorIdAndToLawyerIdAndStatus(
				user.id(), toLawyerId, ContactRequestStatus.PENDIENTE)) {
			throw new ConflictException("Ya tienes una solicitud pendiente con este abogado");
		}

		ContactRequest request = new ContactRequest(doctor, lawyer, message);
		if (caseIdParam != null && !caseIdParam.isBlank()) {
			LegalCase legalCase = caseRepository.findWithPeopleById(UUID.fromString(caseIdParam))
					.orElse(null);
			if (legalCase != null) {
				if (!legalCase.getDoctor().getId().equals(user.id())) {
					throw new ForbiddenException("No puedes vincular una consulta ajena");
				}
				request.setLegalCase(legalCase);
				request.setCaseTitle(legalCase.getTitle());
			}
		}

		request = contactRequestRepository.save(request);
		return enrich(List.of(request)).get(0);
	}

	@Transactional
	public ContactRequestResponse respondContactRequest(AuthenticatedUser user, String requestIdParam,
			String status, String responseMessage) {
		if (requestIdParam == null || status == null) {
			throw new BadRequestException("requestId y status son requeridos");
		}
		if (!"aceptado".equals(status) && !"rechazado".equals(status)) {
			throw new BadRequestException("Status debe ser 'aceptado' o 'rechazado'");
		}

		ContactRequest request = contactRequestRepository.findById(UUID.fromString(requestIdParam))
				.orElseThrow(() -> new NotFoundException("Solicitud no encontrada"));

		// Ownership: solo el abogado destinatario responde (admin también puede)
		if (user.role() != UserRole.ADMIN && !request.getToLawyer().getId().equals(user.id())) {
			throw new ForbiddenException("No tienes permisos para responder esta solicitud");
		}

		request.setStatus(ContactRequestStatus.fromValue(status));
		request.setResponseMessage(responseMessage);
		request = contactRequestRepository.save(request);

		// HU-18: aceptar una solicitud con consulta vinculada asigna al abogado
		// y mueve la consulta a "en_revision".
		LegalCase legalCase = request.getLegalCase();
		if (request.getStatus() == ContactRequestStatus.ACEPTADO && legalCase != null
				&& legalCase.getLawyer() == null) {
			legalCase.setLawyer(request.getToLawyer());
			legalCase.setStatus(CaseStatus.EN_REVISION);
			caseRepository.save(legalCase);
		}

		return enrich(List.of(request)).get(0);
	}

	/** Adjunta perfiles profesionales a las solicitudes en 2 queries (sin N+1). */
	private List<ContactRequestResponse> enrich(List<ContactRequest> requests) {
		List<UUID> doctorIds = requests.stream().map(r -> r.getFromDoctor().getId()).distinct().toList();
		List<UUID> lawyerIds = requests.stream().map(r -> r.getToLawyer().getId()).distinct().toList();

		Map<UUID, DoctorProfile> doctorProfiles = doctorIds.isEmpty() ? Map.of()
				: doctorProfileRepository.findByUserIdIn(doctorIds).stream()
						.collect(Collectors.toMap(d -> d.getUser().getId(), Function.identity()));
		Map<UUID, LawyerProfile> lawyerProfiles = lawyerIds.isEmpty() ? Map.of()
				: lawyerProfileRepository.findByUserIdIn(lawyerIds).stream()
						.collect(Collectors.toMap(l -> l.getUser().getId(), Function.identity()));

		return requests.stream()
				.map(r -> ContactRequestResponse.from(r,
						doctorProfiles.get(r.getFromDoctor().getId()),
						lawyerProfiles.get(r.getToLawyer().getId())))
				.toList();
	}

	private static ContactRequestStatus parseStatus(String value) {
		try {
			return ContactRequestStatus.fromValue(value);
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException(ex.getMessage());
		}
	}
}
