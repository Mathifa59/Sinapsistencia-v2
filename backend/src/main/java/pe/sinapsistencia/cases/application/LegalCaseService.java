package pe.sinapsistencia.cases.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.sinapsistencia.auth.domain.Profile;
import pe.sinapsistencia.auth.domain.UserRole;
import pe.sinapsistencia.auth.infrastructure.ProfileRepository;
import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.cases.domain.CaseContext;
import pe.sinapsistencia.cases.domain.CasePriority;
import pe.sinapsistencia.cases.domain.CaseStatus;
import pe.sinapsistencia.cases.domain.LegalCase;
import pe.sinapsistencia.cases.infrastructure.CaseContextRepository;
import pe.sinapsistencia.cases.infrastructure.LegalCaseRepository;
import pe.sinapsistencia.cases.web.dto.CaseResponse;
import pe.sinapsistencia.cases.web.dto.CreateCaseRequest;
import pe.sinapsistencia.cases.web.dto.UpdateCaseRequest;
import pe.sinapsistencia.shared.api.ListResponse;
import pe.sinapsistencia.shared.exception.BadRequestException;
import pe.sinapsistencia.shared.exception.ForbiddenException;
import pe.sinapsistencia.shared.exception.NotFoundException;

/**
 * Casos de uso de la consulta médico-legal.
 *
 * OWNERSHIP (reemplazo de RLS — falla abierto si se olvida, por eso es
 * explícito en CADA método):
 *   - doctor:  solo sus consultas (doctor_id = usuario autenticado)
 *   - lawyer:  consultas asignadas a él + consultas sin abogado (para matching)
 *   - admin:   todas
 */
@Service
public class LegalCaseService {

	private final LegalCaseRepository caseRepository;
	private final CaseContextRepository contextRepository;
	private final ProfileRepository profileRepository;

	public LegalCaseService(LegalCaseRepository caseRepository,
			CaseContextRepository contextRepository,
			ProfileRepository profileRepository) {
		this.caseRepository = caseRepository;
		this.contextRepository = contextRepository;
		this.profileRepository = profileRepository;
	}

	@Transactional(readOnly = true)
	public ListResponse<CaseResponse> list(AuthenticatedUser user, String status, String priority,
			String doctorId, String search, int page, int pageSize) {

		Specification<LegalCase> spec = ownershipSpec(user);

		if (status != null) {
			CaseStatus statusEnum = parseStatus(status);
			spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), statusEnum));
		}
		if (priority != null) {
			CasePriority priorityEnum = parsePriority(priority);
			spec = spec.and((root, q, cb) -> cb.equal(root.get("priority"), priorityEnum));
		}
		// El filtro doctorId del query param solo tiene efecto para admin;
		// para doctor/lawyer manda el ownership del token.
		if (doctorId != null && user.role() == UserRole.ADMIN) {
			UUID doctorUuid = parseUuid(doctorId);
			spec = spec.and((root, q, cb) -> cb.equal(root.get("doctor").get("id"), doctorUuid));
		}
		if (search != null && !search.isBlank()) {
			String pattern = "%" + search.toLowerCase() + "%";
			spec = spec.and((root, q, cb) -> cb.or(
					cb.like(cb.lower(root.get("title")), pattern),
					cb.like(cb.lower(root.get("description")), pattern)));
		}

		Page<LegalCase> result = caseRepository.findAll(spec,
				PageRequest.of(Math.max(page - 1, 0), pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));

		// Contextos en un solo query (evita N+1)
		List<UUID> caseIds = result.getContent().stream().map(LegalCase::getId).toList();
		Map<UUID, CaseContext> contexts = caseIds.isEmpty() ? Map.of()
				: contextRepository.findByLegalCaseIdIn(caseIds).stream()
						.collect(Collectors.toMap(c -> c.getLegalCase().getId(), Function.identity()));

		List<CaseResponse> items = result.getContent().stream()
				.map(c -> CaseResponse.from(c, contexts.get(c.getId())))
				.toList();

		return ListResponse.paged(items, result.getTotalElements(), page, pageSize);
	}

	@Transactional
	public CaseResponse create(AuthenticatedUser user, CreateCaseRequest request) {
		if (isBlank(request.title()) || isBlank(request.description()) || isBlank(request.priority())) {
			throw new BadRequestException("Título, descripción y prioridad son requeridos");
		}

		Profile doctor = profileRepository.findById(user.id())
				.orElseThrow(() -> new NotFoundException("Perfil no encontrado"));

		LegalCase legalCase = new LegalCase(request.title(), request.description(), doctor);
		legalCase.setPriority(parsePriority(request.priority()));
		legalCase.setMedicalSpecialty(request.medicalSpecialty());
		legalCase.setEventType(request.eventType());
		if (request.perceivedUrgency() != null) {
			legalCase.setPerceivedUrgency(parsePriority(request.perceivedUrgency()));
		}
		legalCase.setNotes(request.notes());
		legalCase = caseRepository.save(legalCase);

		CaseContext context = null;
		if (request.context() != null) {
			var payload = request.context();
			if (isBlank(payload.medicalArea())) {
				throw new BadRequestException("El área médica del contexto es requerida");
			}
			String code = isBlank(payload.contextCode())
					? "Caso-" + legalCase.getId().toString().substring(0, 8)
					: payload.contextCode();
			context = new CaseContext(legalCase, code, payload.medicalArea());
			context.setAgeReference(payload.ageReference());
			context.setEventDate(payload.eventDate());
			context.setSummary(payload.summary() == null ? "" : payload.summary());
			if (payload.relevantFactors() != null) {
				context.setRelevantFactors(payload.relevantFactors());
			}
			context = contextRepository.save(context);
		}

		return CaseResponse.from(legalCase, context);
	}

	@Transactional(readOnly = true)
	public CaseResponse get(AuthenticatedUser user, UUID id) {
		LegalCase legalCase = caseRepository.findWithPeopleById(id)
				.orElseThrow(() -> new NotFoundException("Caso no encontrado"));

		assertCanView(user, legalCase);

		CaseContext context = contextRepository.findByLegalCaseId(id).orElse(null);
		return CaseResponse.from(legalCase, context);
	}

	@Transactional
	public CaseResponse update(AuthenticatedUser user, UUID id, UpdateCaseRequest request) {
		if (request.status() == null && request.lawyerId() == null && request.notes() == null) {
			throw new BadRequestException("No se proporcionaron campos para actualizar");
		}

		LegalCase legalCase = caseRepository.findWithPeopleById(id)
				.orElseThrow(() -> new NotFoundException("Caso no encontrado"));

		assertCanModify(user, legalCase, request);

		if (request.status() != null) {
			legalCase.setStatus(parseStatus(request.status()));
		}
		if (request.lawyerId() != null) {
			UUID lawyerUuid = parseUuid(request.lawyerId());
			Profile lawyer = profileRepository.findById(lawyerUuid)
					.orElseThrow(() -> new NotFoundException("Abogado no encontrado"));
			if (lawyer.getRole() != UserRole.LAWYER) {
				throw new BadRequestException("El usuario asignado debe ser un abogado");
			}
			legalCase.setLawyer(lawyer);
			// Asignar abogado mueve la consulta a "asignada" si aún estaba antes en el flujo
			if (legalCase.getStatus() == CaseStatus.PENDIENTE || legalCase.getStatus() == CaseStatus.CLASIFICADA) {
				legalCase.setStatus(CaseStatus.ASIGNADA);
			}
		}
		if (request.notes() != null) {
			legalCase.setNotes(request.notes());
		}

		legalCase = caseRepository.save(legalCase);
		CaseContext context = contextRepository.findByLegalCaseId(id).orElse(null);
		return CaseResponse.from(legalCase, context);
	}

	// ── Ownership ───────────────────────────────────────────────────────────

	private Specification<LegalCase> ownershipSpec(AuthenticatedUser user) {
		return switch (user.role()) {
			case DOCTOR -> (root, q, cb) -> cb.equal(root.get("doctor").get("id"), user.id());
			case LAWYER -> (root, q, cb) -> cb.or(
					cb.equal(root.get("lawyer").get("id"), user.id()),
					cb.isNull(root.get("lawyer")));
			case ADMIN -> (root, q, cb) -> cb.conjunction();
		};
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

	private void assertCanModify(AuthenticatedUser user, LegalCase legalCase, UpdateCaseRequest request) {
		boolean allowed = switch (user.role()) {
			case DOCTOR -> legalCase.getDoctor().getId().equals(user.id());
			// El abogado puede modificar si está asignado, o auto-asignarse una
			// consulta sin abogado (acepta atenderla, HU-18).
			case LAWYER -> (legalCase.getLawyer() != null && legalCase.getLawyer().getId().equals(user.id()))
					|| (legalCase.getLawyer() == null && request.lawyerId() != null
							&& user.id().toString().equals(request.lawyerId()));
			case ADMIN -> true;
		};
		if (!allowed) {
			throw new ForbiddenException("No tienes permisos para modificar esta consulta");
		}
	}

	// ── Helpers ─────────────────────────────────────────────────────────────

	private static CaseStatus parseStatus(String value) {
		try {
			return CaseStatus.fromValue(value);
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException(ex.getMessage());
		}
	}

	private static CasePriority parsePriority(String value) {
		try {
			return CasePriority.fromValue(value);
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException(ex.getMessage());
		}
	}

	private static UUID parseUuid(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException("Identificador inválido: " + value);
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
