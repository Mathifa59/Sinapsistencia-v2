package pe.sinapsistencia.matching.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.sinapsistencia.auth.domain.UserRole;
import pe.sinapsistencia.auth.infrastructure.ProfileRepository;
import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.cases.domain.CaseStatus;
import pe.sinapsistencia.cases.domain.LegalCase;
import pe.sinapsistencia.cases.infrastructure.CaseContextRepository;
import pe.sinapsistencia.cases.infrastructure.LegalCaseRepository;
import pe.sinapsistencia.matching.domain.ContactRequest;
import pe.sinapsistencia.matching.domain.ContactRequestStatus;
import pe.sinapsistencia.matching.domain.MatchRecommendation;
import pe.sinapsistencia.matching.infrastructure.ContactRequestRepository;
import pe.sinapsistencia.matching.infrastructure.MatchRecommendationRepository;
import pe.sinapsistencia.matching.web.dto.ContactRequestResponse;
import pe.sinapsistencia.matching.web.dto.DoctorCardDto;
import pe.sinapsistencia.matching.web.dto.LawyerCardDto;
import pe.sinapsistencia.matching.web.dto.RecommendationDto;
import pe.sinapsistencia.matching.web.dto.RecommendationDto.RecommendationsResponse;
import pe.sinapsistencia.ml.application.MlProxyService;
import pe.sinapsistencia.profile.domain.DoctorProfile;
import pe.sinapsistencia.profile.domain.LawyerProfile;
import pe.sinapsistencia.profile.infrastructure.DoctorProfileRepository;
import pe.sinapsistencia.profile.infrastructure.LawyerProfileRepository;
import pe.sinapsistencia.shared.exception.BadRequestException;
import pe.sinapsistencia.shared.exception.ConflictException;
import pe.sinapsistencia.shared.exception.ForbiddenException;
import pe.sinapsistencia.shared.exception.NotFoundException;

/**
 * Matching médico-abogado: directorios, recomendaciones ML con explicación XAI
 * (HU-31/32) y fallback de cold-start por especialidad (riesgo 17 del Charter),
 * solicitudes de contacto y casos relevantes para el abogado.
 */
@Service
public class MatchingService {

	private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

	private final DoctorProfileRepository doctorProfileRepository;
	private final LawyerProfileRepository lawyerProfileRepository;
	private final ProfileRepository profileRepository;
	private final ContactRequestRepository contactRequestRepository;
	private final MatchRecommendationRepository recommendationRepository;
	private final LegalCaseRepository caseRepository;
	private final CaseContextRepository contextRepository;
	private final MlProxyService mlProxyService;
	private final ObjectMapper objectMapper;
	private final Random random = new Random();

	public MatchingService(DoctorProfileRepository doctorProfileRepository,
			LawyerProfileRepository lawyerProfileRepository,
			ProfileRepository profileRepository,
			ContactRequestRepository contactRequestRepository,
			MatchRecommendationRepository recommendationRepository,
			LegalCaseRepository caseRepository,
			CaseContextRepository contextRepository,
			MlProxyService mlProxyService,
			ObjectMapper objectMapper) {
		this.doctorProfileRepository = doctorProfileRepository;
		this.lawyerProfileRepository = lawyerProfileRepository;
		this.profileRepository = profileRepository;
		this.contactRequestRepository = contactRequestRepository;
		this.recommendationRepository = recommendationRepository;
		this.caseRepository = caseRepository;
		this.contextRepository = contextRepository;
		this.mlProxyService = mlProxyService;
		this.objectMapper = objectMapper;
	}

	// ── Directorios ─────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public List<DoctorCardDto> listDoctors() {
		return doctorProfileRepository.findAllByOrderByCreatedAtDesc().stream()
				.map(DoctorCardDto::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<LawyerCardDto> listLawyers() {
		return lawyerProfileRepository.findAllByOrderByRatingDesc().stream()
				.map(LawyerCardDto::from)
				.toList();
	}

	// ── Recomendaciones (HU-31/32) ──────────────────────────────────────────

	@Transactional(readOnly = true)
	public RecommendationsResponse recommendations(AuthenticatedUser user, String doctorIdParam) {
		UUID doctorId = resolveDoctor(user, doctorIdParam);
		return computeRecommendations(doctorId);
	}

	/** POST: calcula recomendaciones (para una consulta opcional) y las PERSISTE con factores XAI. */
	@Transactional
	public RecommendationsResponse generateAndPersist(AuthenticatedUser user, String caseIdParam) {
		UUID doctorId = user.role() == UserRole.DOCTOR ? user.id()
				: throwIfNotDoctor(user);

		LegalCase legalCase = null;
		if (caseIdParam != null && !caseIdParam.isBlank()) {
			legalCase = caseRepository.findWithPeopleById(UUID.fromString(caseIdParam))
					.orElseThrow(() -> new NotFoundException("Caso no encontrado"));
			if (!legalCase.getDoctor().getId().equals(doctorId)) {
				throw new ForbiddenException("No puedes generar recomendaciones para una consulta ajena");
			}
		}

		RecommendationsResponse response = computeRecommendations(doctorId);

		var doctorProfileUser = profileRepository.findById(doctorId)
				.orElseThrow(() -> new NotFoundException("Perfil no encontrado"));
		for (RecommendationDto rec : response.recommendations()) {
			var lawyerUser = profileRepository.findById(UUID.fromString(rec.lawyer().userId())).orElse(null);
			if (lawyerUser == null) {
				continue;
			}
			MatchRecommendation entity = new MatchRecommendation(doctorProfileUser, lawyerUser,
					BigDecimal.valueOf(rec.score()));
			entity.setLegalCase(legalCase);
			entity.setReasons(rec.reasons());
			entity.setFactors(rec.featureImportance() == null ? "[]" : rec.featureImportance().toString());
			entity.setAlgorithmVersion(rec.modelUsed());
			recommendationRepository.save(entity);
		}

		// Marca la consulta como clasificada si seguía pendiente (entró al pipeline ML)
		if (legalCase != null && legalCase.getStatus() == CaseStatus.PENDIENTE) {
			legalCase.setStatus(CaseStatus.CLASIFICADA);
			caseRepository.save(legalCase);
		}

		return response;
	}

	private RecommendationsResponse computeRecommendations(UUID doctorId) {
		DoctorProfile doctorProfile = doctorProfileRepository.findByUserId(doctorId)
				.orElseThrow(() -> new NotFoundException("Perfil de médico no encontrado"));

		// Solo abogados disponibles y activos entran al matching (HU-40)
		List<LawyerProfile> lawyers = lawyerProfileRepository.findAllByOrderByRatingDesc().stream()
				.filter(l -> l.isAvailable() && l.getUser().isActive())
				.toList();
		Map<UUID, LawyerProfile> byUserId = lawyers.stream()
				.collect(Collectors.toMap(l -> l.getUser().getId(), Function.identity()));

		// ── Intento ML ──
		try {
			Map<String, Object> profilePayload = new LinkedHashMap<>();
			profilePayload.put("name", doctorProfile.getUser().getName());
			profilePayload.put("specialty", doctorProfile.getSpecialty());
			profilePayload.put("sub_specialties", doctorProfile.getSubSpecialties());
			profilePayload.put("hospital", doctorProfile.getHospital());
			profilePayload.put("years_experience", doctorProfile.getYearsExperience());

			JsonNode mlData = mlProxyService.recommendations(doctorId.toString(), profilePayload, 10);

			List<RecommendationDto> recommendations = new ArrayList<>();
			if (mlData != null && mlData.has("recommendations")) {
				for (JsonNode rec : mlData.get("recommendations")) {
					UUID lawyerUserId = UUID.fromString(rec.get("lawyer_id").asText());
					LawyerProfile lawyer = byUserId.get(lawyerUserId);
					if (lawyer == null) {
						continue;
					}
					recommendations.add(new RecommendationDto(
							"rec-" + doctorId + "-" + lawyerUserId,
							doctorId.toString(),
							LawyerCardDto.from(lawyer),
							(int) Math.round(rec.get("score").asDouble() * 100),
							(int) Math.round(rec.path("content_score").asDouble(0) * 100),
							(int) Math.round(rec.path("collaborative_score").asDouble(0) * 100),
							toStringList(rec.path("matched_specialties")),
							rec.path("model_used").asText("unknown"),
							rec.path("feature_importance"),
							toStringList(rec.path("reasons")),
							Instant.now().toString()));
				}
			}

			Map<String, Object> modelInfo = mlData != null && mlData.has("model_info")
					? objectMapper.convertValue(mlData.get("model_info"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {})
					: Map.of();

			return new RecommendationsResponse(recommendations, modelInfo,
					RecommendationsResponse.ADVISORY_NOTE);
		} catch (Exception ex) {
			log.info("ML no disponible para matching ({}); usando fallback por especialidad", ex.getMessage());
		}

		// ── Fallback cold-start: matching por specialty ↔ medical_areas ──
		String specialty = doctorProfile.getSpecialty().toLowerCase();
		List<RecommendationDto> fallback = lawyers.stream()
				.filter(l -> l.getMedicalAreas().stream().anyMatch(area ->
						area.toLowerCase().contains(specialty) || specialty.contains(area.toLowerCase())))
				.map(l -> new RecommendationDto(
						"rec-" + doctorId + "-" + l.getUser().getId(),
						doctorId.toString(),
						LawyerCardDto.from(l),
						70 + random.nextInt(20),
						0,
						0,
						List.of(),
						"fallback",
						objectMapper.createArrayNode(),
						List.of("Coincidencia por área médica (sin ML service)"),
						Instant.now().toString()))
				.toList();

		return new RecommendationsResponse(fallback,
				Map.of("model", "fallback", "message", "ML service no disponible"),
				RecommendationsResponse.ADVISORY_NOTE);
	}

	// ── Solicitudes de contacto ─────────────────────────────────────────────

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

	// ── Casos relevantes para el abogado ────────────────────────────────────

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

		Map<UUID, pe.sinapsistencia.cases.domain.CaseContext> contexts = cases.isEmpty() ? Map.of()
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

	// ── Helpers ─────────────────────────────────────────────────────────────

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

	private UUID resolveDoctor(AuthenticatedUser user, String doctorIdParam) {
		if (doctorIdParam == null || doctorIdParam.isBlank()) {
			return user.id();
		}
		UUID requested = UUID.fromString(doctorIdParam);
		if (!requested.equals(user.id()) && user.role() != UserRole.ADMIN) {
			throw new ForbiddenException("No puedes pedir recomendaciones para otro médico");
		}
		return requested;
	}

	private UUID throwIfNotDoctor(AuthenticatedUser user) {
		throw new ForbiddenException("Solo un médico puede generar recomendaciones");
	}

	private static ContactRequestStatus parseStatus(String value) {
		try {
			return ContactRequestStatus.fromValue(value);
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException(ex.getMessage());
		}
	}

	private static List<String> toStringList(JsonNode node) {
		List<String> list = new ArrayList<>();
		if (node != null && node.isArray()) {
			node.forEach(item -> list.add(item.asText()));
		}
		return list;
	}
}
