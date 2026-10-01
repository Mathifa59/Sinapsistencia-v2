package pe.sinapsistencia.cases.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import pe.sinapsistencia.auth.domain.Profile;
import pe.sinapsistencia.auth.domain.UserRole;
import pe.sinapsistencia.auth.infrastructure.ProfileRepository;
import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.cases.domain.CaseContext;
import pe.sinapsistencia.cases.domain.CaseEvent;
import pe.sinapsistencia.cases.domain.CasePriority;
import pe.sinapsistencia.cases.domain.CaseStatus;
import pe.sinapsistencia.cases.domain.LegalCase;
import pe.sinapsistencia.cases.domain.LegalResponse;
import pe.sinapsistencia.cases.infrastructure.CaseContextRepository;
import pe.sinapsistencia.cases.infrastructure.CaseEventRepository;
import pe.sinapsistencia.cases.infrastructure.LegalCaseRepository;
import pe.sinapsistencia.cases.infrastructure.LegalResponseRepository;
import pe.sinapsistencia.cases.web.dto.CaseDetailResponse;
import pe.sinapsistencia.cases.web.dto.CaseEventDto;
import pe.sinapsistencia.cases.web.dto.CaseReportDto;
import pe.sinapsistencia.cases.web.dto.CaseResponse;
import pe.sinapsistencia.cases.web.dto.CloseCaseRequest;
import pe.sinapsistencia.cases.web.dto.CreateCaseEventRequest;
import pe.sinapsistencia.cases.web.dto.CreateLegalResponseRequest;
import pe.sinapsistencia.cases.web.dto.EditCaseRequest;
import pe.sinapsistencia.cases.web.dto.LegalResponseDto;
import pe.sinapsistencia.cases.web.dto.MlClassificationDto;
import pe.sinapsistencia.cases.web.dto.TimelineEntryDto;
import pe.sinapsistencia.documents.domain.Document;
import pe.sinapsistencia.documents.infrastructure.DocumentRepository;
import pe.sinapsistencia.matching.application.RecommendationService;
import pe.sinapsistencia.matching.web.dto.RecommendationDto.RecommendationsResponse;
import pe.sinapsistencia.ml.domain.MlClassification;
import pe.sinapsistencia.ml.infrastructure.MlClassificationRepository;
import pe.sinapsistencia.shared.exception.BadRequestException;
import pe.sinapsistencia.shared.exception.ForbiddenException;
import pe.sinapsistencia.shared.exception.NotFoundException;

/** Flujo de consulta: edición, cierre, eventos, respuestas, timeline y reporte (HU-15/21-27/39). */
@Service
public class CaseWorkflowService {

	private final LegalCaseRepository caseRepository;
	private final CaseContextRepository contextRepository;
	private final CaseEventRepository eventRepository;
	private final LegalResponseRepository responseRepository;
	private final MlClassificationRepository classificationRepository;
	private final DocumentRepository documentRepository;
	private final ProfileRepository profileRepository;
	private final RecommendationService recommendationService;
	private final CaseClassificationService classificationService;

	public CaseWorkflowService(LegalCaseRepository caseRepository,
			CaseContextRepository contextRepository,
			CaseEventRepository eventRepository,
			LegalResponseRepository responseRepository,
			MlClassificationRepository classificationRepository,
			DocumentRepository documentRepository,
			ProfileRepository profileRepository,
			RecommendationService recommendationService,
			CaseClassificationService classificationService) {
		this.caseRepository = caseRepository;
		this.contextRepository = contextRepository;
		this.eventRepository = eventRepository;
		this.responseRepository = responseRepository;
		this.classificationRepository = classificationRepository;
		this.documentRepository = documentRepository;
		this.profileRepository = profileRepository;
		this.recommendationService = recommendationService;
		this.classificationService = classificationService;
	}

	@Transactional
	public CaseResponse edit(AuthenticatedUser user, UUID id, EditCaseRequest request) {
		LegalCase legalCase = loadCase(id);
		assertDoctorOwner(user, legalCase);
		if (!canEditBeforeAssignment(legalCase)) {
			throw new BadRequestException(
					"Solo se pueden editar consultas pendientes o clasificadas sin abogado asignado");
		}

		if (request.title() != null && !request.title().isBlank()) {
			legalCase.setTitle(request.title());
		}
		if (request.description() != null && !request.description().isBlank()) {
			legalCase.setDescription(request.description());
		}
		if (request.priority() != null) {
			legalCase.setPriority(LegalCaseService.parsePriorityPublic(request.priority()));
		}
		if (request.medicalSpecialty() != null) {
			legalCase.setMedicalSpecialty(request.medicalSpecialty());
		}
		if (request.eventType() != null) {
			legalCase.setEventType(request.eventType());
		}
		if (request.perceivedUrgency() != null) {
			legalCase.setPerceivedUrgency(LegalCaseService.parsePriorityPublic(request.perceivedUrgency()));
		}
		// H-05: complejidad editada por el médico es, por definición, reportada -- nunca
		// se reclasifica aquí (eso requiere POST /reclassify explícito, ver más abajo).
		if (request.procedureComplexity() != null && !request.procedureComplexity().isBlank()) {
			legalCase.setProcedureComplexity(LegalCaseService.parseComplexityPublic(request.procedureComplexity()));
			legalCase.setComplexitySource("reported");
		}
		if (request.documentationComplete() != null) {
			legalCase.setDocumentationComplete(request.documentationComplete());
		}
		if (request.informedConsent() != null) {
			legalCase.setInformedConsent(request.informedConsent());
		}
		if (request.hasPriorComplaints() != null) {
			legalCase.setHasPriorComplaints(request.hasPriorComplaints());
		}
		if (request.notes() != null) {
			legalCase.setNotes(request.notes());
		}

		CaseContext context = contextRepository.findByLegalCaseId(id).orElse(null);
		if (request.context() != null) {
			var payload = request.context();
			if (context == null) {
				if (isBlank(payload.medicalArea())) {
					throw new BadRequestException("El área médica del contexto es requerida");
				}
				String code = isBlank(payload.contextCode())
						? "Caso-" + legalCase.getId().toString().substring(0, 8)
						: payload.contextCode();
				context = new CaseContext(legalCase, code, payload.medicalArea());
				contextRepository.save(context);
			} else {
				if (payload.contextCode() != null) context.setContextCode(payload.contextCode());
				if (payload.medicalArea() != null) context.setMedicalArea(payload.medicalArea());
				if (payload.ageReference() != null) context.setAgeReference(payload.ageReference());
				if (payload.eventDate() != null) context.setEventDate(payload.eventDate());
				if (payload.summary() != null) context.setSummary(payload.summary());
				if (payload.relevantFactors() != null) context.setRelevantFactors(payload.relevantFactors());
				contextRepository.save(context);
			}
		}

		legalCase = caseRepository.save(legalCase);
		recordSystemEvent(eventRepository, legalCase, legalCase.getDoctor(),
				"edicion", "Consulta editada antes de asignación");

		return CaseResponse.from(legalCase, context);
	}

	/** Abogado asignado inicia revisión: asignada → en_revision (HU-16/18). */
	@Transactional
	public CaseResponse startReview(AuthenticatedUser user, UUID id) {
		LegalCase legalCase = loadCase(id);
		if (user.role() != UserRole.LAWYER && user.role() != UserRole.ADMIN) {
			throw new ForbiddenException("Solo un abogado puede iniciar la revisión");
		}
		if (user.role() == UserRole.LAWYER
				&& (legalCase.getLawyer() == null || !legalCase.getLawyer().getId().equals(user.id()))) {
			throw new ForbiddenException("No estás asignado a esta consulta");
		}
		if (legalCase.getStatus() != CaseStatus.ASIGNADA) {
			throw new BadRequestException("Solo se puede iniciar revisión en consultas asignadas");
		}

		legalCase.setStatus(CaseStatus.EN_REVISION);
		legalCase = caseRepository.save(legalCase);

		Profile lawyer = profileRepository.findById(user.id())
				.orElseThrow(() -> new NotFoundException("Perfil no encontrado"));
		recordSystemEvent(eventRepository, legalCase, lawyer, "inicio_revision",
				"El abogado inició la revisión de la consulta");

		CaseContext context = contextRepository.findByLegalCaseId(id).orElse(null);
		return CaseResponse.from(legalCase, context);
	}

	@Transactional
	public CaseResponse close(AuthenticatedUser user, UUID id, CloseCaseRequest request) {
		LegalCase legalCase = loadCase(id);
		assertCanClose(user, legalCase);
		if (legalCase.getStatus() == CaseStatus.CERRADA) {
			throw new BadRequestException("La consulta ya está cerrada");
		}
		if (legalCase.getStatus() != CaseStatus.RESPONDIDA) {
			throw new BadRequestException("Solo se pueden cerrar consultas respondidas");
		}

		legalCase.setStatus(CaseStatus.CERRADA);
		if (request.reason() != null && !request.reason().isBlank()) {
			String notes = legalCase.getNotes() == null ? "" : legalCase.getNotes() + "\n";
			legalCase.setNotes(notes + "[Cierre] " + request.reason());
		}
		legalCase = caseRepository.save(legalCase);

		Profile actor = profileRepository.findById(user.id()).orElse(legalCase.getDoctor());
		recordSystemEvent(eventRepository, legalCase, actor, "cierre",
				"Cierre de consulta" + (request.reason() != null ? ": " + request.reason() : ""));

		CaseContext context = contextRepository.findByLegalCaseId(id).orElse(null);
		return CaseResponse.from(legalCase, context);
	}

	@Transactional
	public CaseEventDto addEvent(AuthenticatedUser user, UUID caseId, CreateCaseEventRequest request) {
		if (request.eventDate() == null || isBlank(request.eventType())) {
			throw new BadRequestException("Fecha y tipo de evento son requeridos");
		}
		LegalCase legalCase = loadCase(caseId);
		assertCanView(user, legalCase);

		Profile author = profileRepository.findById(user.id())
				.orElseThrow(() -> new NotFoundException("Perfil no encontrado"));

		CaseEvent event = new CaseEvent(legalCase, request.eventDate(), request.eventType(),
				request.description() == null ? "" : request.description(), author);
		event = eventRepository.save(event);
		return CaseEventDto.from(event);
	}

	@Transactional
	public LegalResponseDto addResponse(AuthenticatedUser user, UUID caseId, CreateLegalResponseRequest request) {
		if (isBlank(request.content())) {
			throw new BadRequestException("El contenido de la respuesta es requerido");
		}
		LegalCase legalCase = loadCase(caseId);
		if (user.role() != UserRole.LAWYER && user.role() != UserRole.ADMIN) {
			throw new ForbiddenException("Solo un abogado puede registrar respuestas legales");
		}
		if (user.role() == UserRole.LAWYER
				&& (legalCase.getLawyer() == null || !legalCase.getLawyer().getId().equals(user.id()))) {
			throw new ForbiddenException("No estás asignado a esta consulta");
		}
		if (user.role() != UserRole.ADMIN && legalCase.getStatus() != CaseStatus.EN_REVISION) {
			throw new BadRequestException("Solo se puede responder una consulta en revisión");
		}

		Profile lawyer = profileRepository.findById(user.id())
				.orElseThrow(() -> new NotFoundException("Perfil no encontrado"));

		LegalResponse response = new LegalResponse(legalCase, lawyer, request.content());
		response.setRecommendations(request.recommendations());
		response.setObservations(request.observations());
		response = responseRepository.save(response);

		legalCase.setStatus(CaseStatus.RESPONDIDA);
		caseRepository.save(legalCase);

		recordSystemEvent(eventRepository, legalCase, lawyer, "respuesta_legal",
				"Orientación médico-legal preliminar registrada (no constituye decisión legal definitiva)");

		return LegalResponseDto.from(response);
	}

	@Transactional
	public LegalResponseDto markResponseReviewed(AuthenticatedUser user, UUID caseId, UUID responseId) {
		LegalCase legalCase = loadCase(caseId);
		assertDoctorOwner(user, legalCase);

		LegalResponse response = responseRepository.findById(responseId)
				.orElseThrow(() -> new NotFoundException("Respuesta no encontrada"));
		if (!response.getLegalCase().getId().equals(caseId)) {
			throw new BadRequestException("La respuesta no pertenece a esta consulta");
		}

		response.setReviewed(true);
		response = responseRepository.save(response);

		recordSystemEvent(eventRepository, legalCase, legalCase.getDoctor(),
				"revision_respuesta", "El médico marcó la respuesta como revisada");

		return LegalResponseDto.from(response);
	}

	@Transactional(readOnly = true)
	public CaseDetailResponse getDetail(AuthenticatedUser user, UUID id) {
		LegalCase legalCase = loadCase(id);
		assertCanView(user, legalCase);
		CaseContext context = contextRepository.findByLegalCaseId(id).orElse(null);
		CaseResponse caseData = CaseResponse.from(legalCase, context);

		// H-05: orden con desempate por id -- dos clasificaciones pueden compartir
		// createdAt (p. ej. una reclasificación inmediatamente posterior a la original).
		var latestClassification = classificationRepository
				.findFirstByLegalCase_IdOrderByCreatedAtDescIdDesc(id);
		MlClassificationDto classification = latestClassification.map(MlClassificationDto::from).orElse(null);
		Boolean isStale = latestClassification
				.map(c -> computeIsStale(legalCase, context, c))
				.orElse(null);

		List<LegalResponseDto> responses = responseRepository.findByLegalCase_IdOrderByCreatedAtDesc(id).stream()
				.map(LegalResponseDto::from)
				.toList();

		List<CaseEventDto> events = eventRepository.findByLegalCase_IdOrderByEventDateDescCreatedAtDesc(id).stream()
				.map(CaseEventDto::from)
				.toList();

		List<TimelineEntryDto> timeline = buildTimeline(legalCase, events, responses);

		RecommendationsResponse recommendations = null;
		if (user.role() == UserRole.DOCTOR && legalCase.getDoctor().getId().equals(user.id())) {
			recommendations = recommendationService.recommendations(user,
					legalCase.getDoctor().getId().toString(), legalCase.getId().toString());
		}

		return new CaseDetailResponse(caseData, classification, isStale, responses, events, timeline,
				recommendations, CaseDetailResponse.ADVISORY_NOTE);
	}

	/**
	 * H-05: ¿las entradas que generaron la última clasificación persistida siguen
	 * reflejando el estado actual del caso? Compara contra la fotografía
	 * ({@code inputSnapshot}), nunca recalcula con el reloj del navegador.
	 * Devuelve {@code null} (desconocido, no fabricado) cuando la fotografía es
	 * legacy y no existe.
	 * Visibilidad de paquete a propósito: probado directamente en {@code CaseWorkflowServiceTest}.
	 */
	static Boolean computeIsStale(LegalCase legalCase, CaseContext context, MlClassification classification) {
		JsonNode snapshot = classification.getInputSnapshot();
		JsonNode inputs = snapshot == null ? null : snapshot.get("inputs");
		if (inputs == null || inputs.isNull()) {
			return null;
		}

		CasePriority perceived = legalCase.getPerceivedUrgency() != null
				? legalCase.getPerceivedUrgency()
				: legalCase.getPriority();
		String currentSpecialty = CaseClassificationService.resolveSpecialty(legalCase, context);
		String currentComplexity = legalCase.getProcedureComplexity() == null
				? null
				: legalCase.getProcedureComplexity().getValue();
		String currentEventDate = context != null && context.getEventDate() != null
				? context.getEventDate().toString()
				: null;
		String snapshotEventDate = snapshot.hasNonNull("eventDate") ? snapshot.get("eventDate").asText() : null;

		return !textFieldEquals(inputs, "specialty", currentSpecialty)
				|| !textFieldEquals(inputs, "procedure_complexity", currentComplexity)
				|| !textFieldEquals(inputs, "priority", perceived.getValue())
				|| !boolFieldEquals(inputs, "documentation_complete", legalCase.isDocumentationComplete())
				|| !boolFieldEquals(inputs, "informed_consent", legalCase.isInformedConsent())
				|| !boolFieldEquals(inputs, "has_prior_complaints", legalCase.isHasPriorComplaints())
				|| !Objects.equals(snapshotEventDate, currentEventDate);
	}

	private static boolean textFieldEquals(JsonNode inputs, String field, String expected) {
		JsonNode value = inputs.get(field);
		String actual = value == null || value.isNull() ? null : value.asText();
		return Objects.equals(actual, expected);
	}

	private static boolean boolFieldEquals(JsonNode inputs, String field, boolean expected) {
		JsonNode value = inputs.get(field);
		boolean actual = value != null && value.asBoolean();
		return actual == expected;
	}

	/**
	 * H-05: reevaluación explícita a pedido del médico propietario -- nunca automática ni
	 * disparada por una lectura. No sobrescribe la clasificación anterior: crea una nueva
	 * fila y la vincula al caso vía el mismo flujo de {@code classifyAndPrioritize}, además
	 * de un evento propio que distingue esta acción de la clasificación automática al crear.
	 */
	@Transactional
	public CaseDetailResponse reclassify(AuthenticatedUser user, UUID id) {
		LegalCase legalCase = loadCase(id);
		assertDoctorOwner(user, legalCase);
		if (!canEditBeforeAssignment(legalCase)) {
			throw new BadRequestException(
					"Solo se puede reevaluar el riesgo de consultas pendientes o clasificadas sin abogado asignado");
		}

		CaseContext context = contextRepository.findByLegalCaseId(id).orElse(null);
		classificationService.classifyAndPrioritize(legalCase, context);

		recordSystemEvent(eventRepository, legalCase, legalCase.getDoctor(), "reclasificacion",
				"El médico solicitó una reevaluación de riesgo con las entradas actuales del caso");

		return getDetail(user, id);
	}

	/** H-05: historial completo de clasificaciones del caso -- ninguna se sobrescribe ni se borra. */
	@Transactional(readOnly = true)
	public List<MlClassificationDto> getClassificationHistory(AuthenticatedUser user, UUID id) {
		LegalCase legalCase = loadCase(id);
		assertCanView(user, legalCase);
		return classificationRepository.findByLegalCase_IdOrderByCreatedAtDescIdDesc(id).stream()
				.map(MlClassificationDto::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<TimelineEntryDto> getTimeline(AuthenticatedUser user, UUID id) {
		LegalCase legalCase = loadCase(id);
		assertCanView(user, legalCase);
		List<CaseEventDto> events = eventRepository.findByLegalCase_IdOrderByEventDateDescCreatedAtDesc(id).stream()
				.map(CaseEventDto::from)
				.toList();
		List<LegalResponseDto> responses = responseRepository.findByLegalCase_IdOrderByCreatedAtDesc(id).stream()
				.map(LegalResponseDto::from)
				.toList();
		return buildTimeline(legalCase, events, responses);
	}

	@Transactional(readOnly = true)
	public CaseReportDto getReport(AuthenticatedUser user, UUID id) {
		CaseDetailResponse detail = getDetail(user, id);
		List<String> documentTitles = documentRepository.findByLegalCase_IdOrderByUpdatedAtDesc(id).stream()
				.map(Document::getTitle)
				.toList();

		return new CaseReportDto(
				detail.caseData(),
				detail.classification(),
				detail.responses(),
				detail.timeline(),
				documentTitles,
				CaseDetailResponse.ADVISORY_NOTE,
				Instant.now());
	}

	private List<TimelineEntryDto> buildTimeline(LegalCase legalCase, List<CaseEventDto> events,
			List<LegalResponseDto> responses) {
		List<TimelineEntryDto> timeline = new ArrayList<>();
		timeline.add(new TimelineEntryDto(
				"created-" + legalCase.getId(),
				"creacion",
				"Consulta registrada",
				legalCase.getTitle(),
				legalCase.getCreatedAt(),
				legalCase.getDoctor().getName()));

		for (CaseEventDto event : events) {
			timeline.add(new TimelineEntryDto(
					event.id(),
					"evento",
					event.eventType(),
					event.description(),
					event.createdAt(),
					event.createdByName()));
		}
		for (LegalResponseDto response : responses) {
			timeline.add(new TimelineEntryDto(
					response.id(),
					"respuesta",
					"Respuesta legal preliminar",
					response.content(),
					response.createdAt(),
					response.lawyerName()));
		}
		if (legalCase.getStatus() == CaseStatus.CERRADA) {
			timeline.add(new TimelineEntryDto(
					"closed-" + legalCase.getId(),
					"cierre",
					"Consulta cerrada",
					"Estado final: cerrada",
					legalCase.getUpdatedAt(),
					null));
		}

		timeline.sort(Comparator.comparing(TimelineEntryDto::occurredAt,
				Comparator.nullsLast(Comparator.reverseOrder())));
		return timeline;
	}

	public static void recordSystemEvent(CaseEventRepository eventRepository, LegalCase legalCase,
			Profile actor, String eventType, String description) {
		CaseEvent event = new CaseEvent(
				legalCase,
				LocalDate.now(),
				eventType,
				description,
				actor);
		eventRepository.save(event);
	}

	private LegalCase loadCase(UUID id) {
		return caseRepository.findWithPeopleById(id)
				.orElseThrow(() -> new NotFoundException("Caso no encontrado"));
	}

	private void assertDoctorOwner(AuthenticatedUser user, LegalCase legalCase) {
		if (user.role() != UserRole.ADMIN && !legalCase.getDoctor().getId().equals(user.id())) {
			throw new ForbiddenException("No tienes permisos sobre esta consulta");
		}
	}

	private void assertCanView(AuthenticatedUser user, LegalCase legalCase) {
		boolean allowed = switch (user.role()) {
			case DOCTOR -> legalCase.getDoctor().getId().equals(user.id());
			case LAWYER -> legalCase.getLawyer() == null || legalCase.getLawyer().getId().equals(user.id());
			case ADMIN -> true;
		};
		if (!allowed) {
			throw new ForbiddenException("No tienes permisos para ver esta consulta");
		}
	}

	private void assertCanClose(AuthenticatedUser user, LegalCase legalCase) {
		boolean allowed = switch (user.role()) {
			case DOCTOR -> legalCase.getDoctor().getId().equals(user.id());
			case LAWYER -> legalCase.getLawyer() != null && legalCase.getLawyer().getId().equals(user.id());
			case ADMIN -> true;
		};
		if (!allowed) {
			throw new ForbiddenException("No tienes permisos para cerrar esta consulta");
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static boolean canEditBeforeAssignment(LegalCase legalCase) {
		return legalCase.getLawyer() == null
				&& (legalCase.getStatus() == CaseStatus.PENDIENTE
						|| legalCase.getStatus() == CaseStatus.CLASIFICADA);
	}
}
