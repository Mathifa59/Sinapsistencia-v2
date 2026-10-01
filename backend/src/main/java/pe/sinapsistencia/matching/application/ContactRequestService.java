package pe.sinapsistencia.matching.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.sinapsistencia.auth.domain.UserRole;
import pe.sinapsistencia.auth.infrastructure.ProfileRepository;
import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.cases.application.CaseWorkflowService;
import pe.sinapsistencia.cases.domain.CaseStatus;
import pe.sinapsistencia.cases.domain.LegalCase;
import pe.sinapsistencia.cases.infrastructure.CaseEventRepository;
import pe.sinapsistencia.cases.infrastructure.LegalCaseRepository;
import pe.sinapsistencia.matching.domain.ContactRequest;
import pe.sinapsistencia.matching.domain.ContactRequestStatus;
import pe.sinapsistencia.matching.domain.MatchRecommendation;
import pe.sinapsistencia.matching.domain.RecommendationRun;
import pe.sinapsistencia.matching.domain.RecommendationRunStatus;
import pe.sinapsistencia.matching.infrastructure.ContactRequestRepository;
import pe.sinapsistencia.matching.infrastructure.MatchRecommendationRepository;
import pe.sinapsistencia.matching.web.dto.ContactRequestResponse;
import pe.sinapsistencia.notifications.MailTemplates;
import pe.sinapsistencia.notifications.NotificationService;
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
	private final CaseEventRepository eventRepository;
	private final DoctorProfileRepository doctorProfileRepository;
	private final LawyerProfileRepository lawyerProfileRepository;
	private final MatchRecommendationRepository matchRecommendationRepository;
	private final NotificationService notificationService;
	private final String frontendUrl;

	public ContactRequestService(ContactRequestRepository contactRequestRepository,
			ProfileRepository profileRepository,
			LegalCaseRepository caseRepository,
			CaseEventRepository eventRepository,
			DoctorProfileRepository doctorProfileRepository,
			LawyerProfileRepository lawyerProfileRepository,
			MatchRecommendationRepository matchRecommendationRepository,
			NotificationService notificationService,
			@Value("${app.frontend.url:http://localhost:4200}") String frontendUrl) {
		this.contactRequestRepository = contactRequestRepository;
		this.profileRepository = profileRepository;
		this.caseRepository = caseRepository;
		this.eventRepository = eventRepository;
		this.doctorProfileRepository = doctorProfileRepository;
		this.lawyerProfileRepository = lawyerProfileRepository;
		this.matchRecommendationRepository = matchRecommendationRepository;
		this.notificationService = notificationService;
		this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
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
			String message, String caseIdParam, String recommendationIdParam, String selectionSourceParam) {
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
					.orElseThrow(() -> new NotFoundException("Consulta no encontrada"));
			if (!legalCase.getDoctor().getId().equals(user.id())) {
				throw new ForbiddenException("No puedes vincular una consulta ajena");
			}
			if (legalCase.getLawyer() != null) {
				throw new BadRequestException("La consulta ya tiene un abogado asignado");
			}
			if (legalCase.getStatus() != CaseStatus.PENDIENTE && legalCase.getStatus() != CaseStatus.CLASIFICADA) {
				throw new BadRequestException("Solo se pueden vincular consultas pendientes o clasificadas");
			}
			request.setLegalCase(legalCase);
			request.setCaseTitle(legalCase.getTitle());
		} else {
			var openCase = caseRepository.findFirstByDoctor_IdAndLawyerIsNullAndStatusInOrderByCreatedAtDesc(
					user.id(), List.of(CaseStatus.PENDIENTE, CaseStatus.CLASIFICADA));
			if (openCase.isPresent()) {
				LegalCase legalCase = openCase.get();
				request.setLegalCase(legalCase);
				request.setCaseTitle(legalCase.getTitle());
			}
		}

		// H-02: vincula la solicitud a la recomendación concreta que el médico vio
		// al elegir -- la puntuación se COPIA del registro del servidor, nunca se
		// acepta una cifra enviada por el cliente (RF-02.4, el frontend no envía mlScore).
		applySelection(request, user, toLawyerId, lawyer, recommendationIdParam, selectionSourceParam);

		request = contactRequestRepository.save(request);

		// H-06: encolado en la transacción de negocio, tras obtener el ID del
		// recurso -- NotificationWorker despacha DESPUÉS del commit. Reply-To =
		// correo real del médico, para que médico y abogado se correspondan
		// directo por correo (antes era un envío directo @Async; ya no corre en
		// paralelo con el outbox).
		notificationService.enqueue(
				"contact_request_received:" + request.getId(),
				"contact_request_received",
				request.getLegalCase() == null ? null : request.getLegalCase().getId(),
				"contact_request", request.getId(),
				lawyer.getEmail(), doctor.getEmail(),
				"Nueva solicitud de contacto — Sinapsistencia",
				MailTemplates.contactRequestReceived(lawyer.getName(), doctor.getName(), request.getCaseTitle(),
						message, frontendUrl + "/lawyer/requests"));

		return enrich(List.of(request)).get(0);
	}

	/**
	 * H-02: resuelve el origen de la selección y, si viene del ranking, valida la
	 * relación completa (ejecución completada, médico/caso/abogado coinciden,
	 * abogado sigue disponible y activo -- RF-02.6) antes de copiar su score al
	 * FK/{@code mlScore}. Bodies legacy sin estos campos quedan {@code legacy_untracked}.
	 */
	private void applySelection(ContactRequest request, AuthenticatedUser user, UUID toLawyerId,
			pe.sinapsistencia.auth.domain.Profile lawyer, String recommendationIdParam, String selectionSourceParam) {
		if (recommendationIdParam != null && !recommendationIdParam.isBlank()) {
			if (selectionSourceParam != null && !"recommendation".equals(selectionSourceParam)) {
				throw new BadRequestException(
						"selectionSource debe ser 'recommendation' cuando se envía recommendationId");
			}
			MatchRecommendation recommendation = matchRecommendationRepository
					.findById(UUID.fromString(recommendationIdParam))
					.orElseThrow(() -> new NotFoundException("Recomendación no encontrada"));

			RecommendationRun run = recommendation.getRun();
			if (run == null || run.getStatus() != RecommendationRunStatus.COMPLETED) {
				throw new BadRequestException("La recomendación indicada no pertenece a una ejecución completada");
			}
			if (!run.getDoctor().getId().equals(user.id())) {
				throw new ForbiddenException("La recomendación no pertenece a tu ejecución");
			}
			if (request.getLegalCase() == null || !run.getLegalCase().getId().equals(request.getLegalCase().getId())) {
				throw new BadRequestException("La recomendación no corresponde a la consulta indicada");
			}
			if (!recommendation.getLawyer().getId().equals(toLawyerId)) {
				throw new BadRequestException("La recomendación no corresponde al abogado indicado");
			}

			LawyerProfile lawyerProfile = lawyerProfileRepository.findByUserId(toLawyerId).orElse(null);
			if (lawyerProfile == null || !lawyerProfile.isAvailable() || !lawyer.isActive()) {
				throw new ConflictException(
						"El abogado ya no está disponible; genera un nuevo ranking para elegir otro.");
			}

			request.setRecommendation(recommendation);
			request.setSelectionSource("recommendation");
			if (recommendation.getScoreRaw() != null) {
				request.setMlScore(recommendation.getScoreRaw().multiply(BigDecimal.valueOf(100))
						.setScale(2, RoundingMode.HALF_UP));
			}
			return;
		}

		if ("directory".equals(selectionSourceParam)) {
			request.setSelectionSource("directory");
			return;
		}
		if (selectionSourceParam != null && !selectionSourceParam.isBlank()) {
			throw new BadRequestException("selectionSource inválido: " + selectionSourceParam);
		}
		// Body legacy (cliente anterior a H-02): origen desconocido, nunca se finge ML.
		request.setSelectionSource("legacy_untracked");
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
		if ("rechazado".equals(status) && (responseMessage == null || responseMessage.isBlank())) {
			throw new BadRequestException("Debe indicar el motivo del rechazo");
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

		// HU-18: aceptar asigna al abogado y mueve la consulta a "asignada".
		LegalCase legalCase = request.getLegalCase();
		if (request.getStatus() == ContactRequestStatus.ACEPTADO && legalCase != null
				&& legalCase.getLawyer() == null) {
			legalCase.setLawyer(request.getToLawyer());
			legalCase.setStatus(CaseStatus.ASIGNADA);
			caseRepository.save(legalCase);
			CaseWorkflowService.recordSystemEvent(eventRepository, legalCase, request.getToLawyer(),
					"asignacion", "Abogado asignado tras aceptar la solicitud de contacto");
		}

		// H-06: encolado tras el save -- Reply-To = correo real del abogado que
		// respondió, misma razón que al recibir la solicitud.
		boolean accepted = request.getStatus() == ContactRequestStatus.ACEPTADO;
		notificationService.enqueue(
				"contact_request_answered:" + request.getId() + ":" + request.getStatus().getValue(),
				"contact_request_answered",
				request.getLegalCase() == null ? null : request.getLegalCase().getId(),
				"contact_request", request.getId(),
				request.getFromDoctor().getEmail(), request.getToLawyer().getEmail(),
				accepted ? "Tu solicitud de contacto fue aceptada — Sinapsistencia"
						: "Respuesta a tu solicitud de contacto — Sinapsistencia",
				MailTemplates.contactRequestAnswered(request.getFromDoctor().getName(), request.getToLawyer().getName(),
						request.getCaseTitle(), accepted, responseMessage, frontendUrl + "/doctor/cases"));

		return enrich(List.of(request)).get(0);
	}

	/** HU-16: el médico cancela una solicitud propia aún pendiente (soft, sin correo). */
	@Transactional
	public ContactRequestResponse cancelContactRequest(AuthenticatedUser user, String requestIdParam) {
		if (requestIdParam == null) {
			throw new BadRequestException("requestId es requerido");
		}

		ContactRequest request = contactRequestRepository.findById(UUID.fromString(requestIdParam))
				.orElseThrow(() -> new NotFoundException("Solicitud no encontrada"));

		// Ownership: solo el médico que la envió (o admin) puede cancelarla.
		if (user.role() != UserRole.ADMIN && !request.getFromDoctor().getId().equals(user.id())) {
			throw new ForbiddenException("No tienes permisos para cancelar esta solicitud");
		}
		if (request.getStatus() != ContactRequestStatus.PENDIENTE) {
			throw new BadRequestException("Solo se pueden cancelar solicitudes pendientes");
		}

		request.setStatus(ContactRequestStatus.CANCELADO);
		request = contactRequestRepository.save(request);
		return enrich(List.of(request)).get(0);
	}

	/**
	 * Admin: elimina definitivamente una solicitud (limpieza de datos de demo).
	 * Si estaba aceptada y había asignado el abogado a la consulta, revierte esa
	 * asignación (la consulta vuelve a estar disponible).
	 */
	@Transactional
	public void adminDeleteContactRequest(String requestIdParam) {
		ContactRequest request = contactRequestRepository.findById(UUID.fromString(requestIdParam))
				.orElseThrow(() -> new NotFoundException("Solicitud no encontrada"));

		LegalCase legalCase = request.getLegalCase();
		if (request.getStatus() == ContactRequestStatus.ACEPTADO && legalCase != null
				&& legalCase.getLawyer() != null
				&& legalCase.getLawyer().getId().equals(request.getToLawyer().getId())) {
			legalCase.setLawyer(null);
			legalCase.setStatus(CaseStatus.CLASIFICADA);
			caseRepository.save(legalCase);
		}

		contactRequestRepository.delete(request);
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
