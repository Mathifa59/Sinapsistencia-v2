package pe.sinapsistencia.matching.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.sinapsistencia.auth.domain.Profile;
import pe.sinapsistencia.auth.domain.UserRole;
import pe.sinapsistencia.auth.infrastructure.ProfileRepository;
import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.cases.domain.CaseStatus;
import pe.sinapsistencia.cases.domain.LegalCase;
import pe.sinapsistencia.cases.infrastructure.LegalCaseRepository;
import pe.sinapsistencia.matching.domain.MatchRecommendation;
import pe.sinapsistencia.matching.domain.RecommendationRun;
import pe.sinapsistencia.matching.domain.RecommendationRunStatus;
import pe.sinapsistencia.matching.infrastructure.MatchRecommendationRepository;
import pe.sinapsistencia.matching.infrastructure.RecommendationRunRepository;
import pe.sinapsistencia.matching.web.dto.LawyerCardDto;
import pe.sinapsistencia.matching.web.dto.RecommendationDto;
import pe.sinapsistencia.matching.web.dto.RecommendationDto.RecommendationsResponse;
import pe.sinapsistencia.matching.web.dto.RecommendationRunDto;
import pe.sinapsistencia.matching.web.dto.RecommendationRunSummaryDto;
import pe.sinapsistencia.ml.application.MlProxyService;
import pe.sinapsistencia.profile.domain.DoctorProfile;
import pe.sinapsistencia.profile.domain.LawyerProfile;
import pe.sinapsistencia.profile.infrastructure.DoctorProfileRepository;
import pe.sinapsistencia.profile.infrastructure.LawyerProfileRepository;
import pe.sinapsistencia.shared.exception.BadRequestException;
import pe.sinapsistencia.shared.exception.ForbiddenException;
import pe.sinapsistencia.shared.exception.NotFoundException;

/**
 * H-02: generación y lectura de recomendaciones médico↔abogado vía ML
 * (TF-IDF + similitud coseno) -- persistidas como {@link RecommendationRun}
 * reproducibles. Una lectura (GET) NUNCA invoca ML ni crea estado: solo
 * {@link #generateRun} (POST, idempotente) ejecuta el modelo.
 *
 * <p>La fórmula de puntaje (0.70 similitud de contenido + 0.30 desempeño) vive
 * exclusivamente en {@code ml-service/app/matching/model.py} (W_CONTENT/
 * W_PERFORMANCE) y en el fallback determinístico de este archivo
 * ({@link #fallbackScore}) -- ninguno de los dos se modifica aquí; este
 * servicio solo adquiere/persiste/transporta lo que esos cálculos producen.
 */
@Service
public class RecommendationService {

	private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

	private static final int TOP_K = 10;
	private static final String PIPELINE_VERSION = "matching-pipeline-v3";
	private static final com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> MAP_TYPE =
			new com.fasterxml.jackson.core.type.TypeReference<>() {
			};

	private final DoctorProfileRepository doctorProfileRepository;
	private final LawyerProfileRepository lawyerProfileRepository;
	private final ProfileRepository profileRepository;
	private final MatchRecommendationRepository recommendationRepository;
	private final LegalCaseRepository caseRepository;
	private final RecommendationRunRepository runRepository;
	private final RecommendationRunService runService;
	private final MlProxyService mlProxyService;
	private final ObjectMapper objectMapper;

	public RecommendationService(DoctorProfileRepository doctorProfileRepository,
			LawyerProfileRepository lawyerProfileRepository,
			ProfileRepository profileRepository,
			MatchRecommendationRepository recommendationRepository,
			LegalCaseRepository caseRepository,
			RecommendationRunRepository runRepository,
			RecommendationRunService runService,
			MlProxyService mlProxyService,
			ObjectMapper objectMapper) {
		this.doctorProfileRepository = doctorProfileRepository;
		this.lawyerProfileRepository = lawyerProfileRepository;
		this.profileRepository = profileRepository;
		this.recommendationRepository = recommendationRepository;
		this.caseRepository = caseRepository;
		this.runRepository = runRepository;
		this.runService = runService;
		this.mlProxyService = mlProxyService;
		this.objectMapper = objectMapper;
	}

	// ── Lecturas: nunca invocan ML, nunca crean una ejecución ──────────────────

	/**
	 * GET /api/matching/lawyers?doctorId=&caseId= (y {@code CaseWorkflowService.getDetail}):
	 * última ejecución COMPLETADA del caso, o una respuesta vacía con
	 * {@code status: no_run_available} si todavía no se generó ninguna (RF-02.7).
	 * Sin {@code caseId} (resumen general, ej. dashboard): última ejecución
	 * completada del médico en cualquiera de sus casos -- sigue sin recalcular.
	 */
	@Transactional(readOnly = true)
	public RecommendationsResponse recommendations(AuthenticatedUser user, String doctorIdParam, String caseIdParam) {
		UUID doctorId = resolveDoctor(user, doctorIdParam);
		if (caseIdParam == null || caseIdParam.isBlank()) {
			return runRepository.findFirstByDoctor_IdAndStatusOrderByCreatedAtDescIdDesc(doctorId,
					RecommendationRunStatus.COMPLETED)
					.map(this::toRecommendationsResponse)
					.orElseGet(() -> emptyResponse("Todavía no se generó ningún ranking para este médico."));
		}
		LegalCase legalCase = resolveCaseForDoctor(user, doctorId, caseIdParam);
		return latestRunAsResponse(legalCase.getId());
	}

	@Transactional(readOnly = true)
	public List<RecommendationRunSummaryDto> recommendationRuns(AuthenticatedUser user, String caseIdParam) {
		LegalCase legalCase = loadCaseForMatchingView(user, caseIdParam);
		return runRepository.findByLegalCase_IdOrderByCreatedAtDescIdDesc(legalCase.getId()).stream()
				.map(RecommendationService::toSummaryDto)
				.toList();
	}

	@Transactional(readOnly = true)
	public RecommendationRunDto recommendationRun(AuthenticatedUser user, String runIdParam) {
		RecommendationRun run = runRepository.findById(parseUuid(runIdParam, "runId"))
				.orElseThrow(() -> new NotFoundException("Ejecución no encontrada"));
		assertCanViewMatching(user, run.getLegalCase());
		List<MatchRecommendation> rows = recommendationRepository.findByRun_IdOrderByRank(run.getId());
		return toRunDto(run, rows);
	}

	private RecommendationsResponse latestRunAsResponse(UUID caseId) {
		return runRepository
				.findFirstByLegalCase_IdAndStatusOrderByCreatedAtDescIdDesc(caseId, RecommendationRunStatus.COMPLETED)
				.map(this::toRecommendationsResponse)
				.orElseGet(() -> emptyResponse("Todavía no se generó un ranking para este caso."));
	}

	private RecommendationsResponse toRecommendationsResponse(RecommendationRun run) {
		List<MatchRecommendation> rows = recommendationRepository.findByRun_IdOrderByRank(run.getId());
		List<RecommendationDto> recs = rows.stream()
				.map(r -> toRecommendationDto(r, run))
				.toList();
		Map<String, Object> modelInfo = new LinkedHashMap<>(toMap(run.getModelInfo()));
		modelInfo.put("runId", run.getId().toString());
		modelInfo.put("caseId", run.getLegalCase().getId().toString());
		modelInfo.put("status", run.getStatus().getValue());
		modelInfo.put("origin", run.getOrigin());
		modelInfo.put("weights", toMap(run.getWeights()));
		return new RecommendationsResponse(recs, modelInfo, RecommendationsResponse.ADVISORY_NOTE);
	}

	private static RecommendationsResponse emptyResponse(String message) {
		return new RecommendationsResponse(List.of(),
				Map.of("status", "no_run_available", "message", message),
				RecommendationsResponse.ADVISORY_NOTE);
	}

	// ── Escritura: única vía que invoca ML, idempotente por clave del cliente ──

	/**
	 * POST /api/matching/lawyers: genera una ejecución nueva (o reutiliza la
	 * completada de la misma clave+comando). Nunca recalcula dentro de una
	 * transacción larga: adquiere la fila {@code processing} primero
	 * ({@link RecommendationRunService#acquire}), invoca ML/fallback FUERA de
	 * transacción, y finaliza en una segunda transacción atómica.
	 */
	public RecommendationRunDto generateRun(AuthenticatedUser user, String caseIdParam, String idempotencyKey) {
		if (user.role() != UserRole.DOCTOR) {
			throw new ForbiddenException("Solo un médico puede generar recomendaciones");
		}
		if (caseIdParam == null || caseIdParam.isBlank()) {
			throw new BadRequestException("caseId es requerido para generar recomendaciones");
		}
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new BadRequestException("idempotencyKey es requerido");
		}

		UUID doctorId = user.id();
		LegalCase legalCase = caseRepository.findWithPeopleById(parseUuid(caseIdParam, "caseId"))
				.orElseThrow(() -> new NotFoundException("Caso no encontrado"));
		if (!legalCase.getDoctor().getId().equals(doctorId)) {
			throw new ForbiddenException("No puedes generar recomendaciones para una consulta ajena");
		}
		Profile doctor = profileRepository.findById(doctorId)
				.orElseThrow(() -> new NotFoundException("Perfil no encontrado"));

		String requestHash = sha256(legalCase.getId().toString());
		RecommendationRunService.AcquireResult acquired = runService.acquire(doctor, legalCase, idempotencyKey,
				requestHash, TOP_K, PIPELINE_VERSION);

		RecommendationRun run = acquired.run();
		if (!acquired.reused()) {
			try {
				ComputedBundle computed = computeViaMlOrFallback(doctorId, legalCase);
				Map<UUID, Profile> lawyerUserById = profileRepository
						.findAllById(computed.items().stream()
								.map(RecommendationRunService.ComputedItem::lawyerUserId)
								.toList())
						.stream()
						.collect(Collectors.toMap(Profile::getId, Function.identity()));

				String inputHash = sha256(toJsonString(computed.inputsMap()));
				String corpusHash = sha256(toJsonString(computed.corpusList()));
				RecommendationRunService.ComputedResult result = new RecommendationRunService.ComputedResult(
						computed.items(), computed.origin(), computed.modelVersion(),
						objectMapper.valueToTree(computed.weightsMap()), objectMapper.valueToTree(computed.modelInfoMap()),
						objectMapper.valueToTree(computed.inputsMap()), objectMapper.valueToTree(computed.corpusList()),
						inputHash, corpusHash, computed.candidateCount());

				run = runService.complete(run.getId(), result, lawyerUserById);
				markCaseEnteredPipelineIfNeeded(legalCase.getId());
			} catch (Exception ex) {
				log.warn("Fallo generando recomendaciones para el run {}: {}", run.getId(), ex.getMessage());
				runService.markFailed(run.getId(), "generation_error", sanitizeError(ex.getMessage()));
				throw ex;
			}
		}

		List<MatchRecommendation> rows = recommendationRepository.findByRun_IdOrderByRank(run.getId());
		return toRunDto(run, rows);
	}

	private void markCaseEnteredPipelineIfNeeded(UUID caseId) {
		caseRepository.findById(caseId).ifPresent(legalCase -> {
			if (legalCase.getStatus() == CaseStatus.PENDIENTE) {
				legalCase.setStatus(CaseStatus.CLASIFICADA);
				caseRepository.save(legalCase);
			}
		});
	}

	// ── Cómputo puro: ML o fallback determinístico -- sin IDs ni persistencia ──

	private record ComputedBundle(
			List<RecommendationRunService.ComputedItem> items,
			String origin,
			String modelVersion,
			Map<String, Object> weightsMap,
			Map<String, Object> modelInfoMap,
			Map<String, Object> inputsMap,
			List<Map<String, Object>> corpusList,
			int candidateCount) {
	}

	/**
	 * Calcula recomendaciones para un médico. Si se pasa una consulta (caso), la
	 * especialidad y el tipo de evento DEL CASO mandan sobre el perfil fijo del
	 * médico — dos consultas distintas del mismo médico deben poder recomendar
	 * abogados distintos (HU-31/32).
	 */
	private ComputedBundle computeViaMlOrFallback(UUID doctorId, LegalCase legalCase) {
		DoctorProfile doctorProfile = doctorProfileRepository.findByUserId(doctorId)
				.orElseThrow(() -> new NotFoundException("Perfil de médico no encontrado"));

		String specialty = legalCase != null && legalCase.getMedicalSpecialty() != null
				&& !legalCase.getMedicalSpecialty().isBlank()
				? legalCase.getMedicalSpecialty()
				: doctorProfile.getSpecialty();

		List<String> subSpecialties = new ArrayList<>(doctorProfile.getSubSpecialties());
		if (legalCase != null && legalCase.getEventType() != null && !legalCase.getEventType().isBlank()) {
			subSpecialties.add(legalCase.getEventType());
		}

		// Solo abogados disponibles y activos entran al matching (HU-40)
		List<LawyerProfile> lawyers = lawyerProfileRepository.findAllByOrderByRatingDesc().stream()
				.filter(l -> l.isAvailable() && l.getUser().isActive())
				.toList();
		Map<UUID, LawyerProfile> byUserId = lawyers.stream()
				.collect(Collectors.toMap(l -> l.getUser().getId(), Function.identity()));

		Map<String, Object> profilePayload = new LinkedHashMap<>();
		profilePayload.put("name", doctorProfile.getUser().getName());
		profilePayload.put("specialty", specialty);
		profilePayload.put("sub_specialties", subSpecialties);
		profilePayload.put("hospital", doctorProfile.getHospital());
		profilePayload.put("years_experience", doctorProfile.getYearsExperience());
		// #4: el TEXTO del caso entra al vector TF-IDF (no solo la especialidad).
		profilePayload.put("case_text", buildCaseText(legalCase));

		// #3: corpus VIVO — la lista real de abogados de la BD viaja en cada
		// matching (incluye a los registrados por la app y ratings actuales).
		List<Map<String, Object>> lawyerCorpus = lawyers.stream()
				.<Map<String, Object>>map(l -> {
					Map<String, Object> item = new LinkedHashMap<>();
					item.put("lawyer_id", l.getUser().getId().toString());
					item.put("name", l.getUser().getName());
					item.put("specialties", l.getSpecialties());
					item.put("medical_areas", l.getMedicalAreas());
					item.put("bio", l.getBio() == null ? "" : l.getBio());
					item.put("rating", l.getRating() == null ? 0.0 : l.getRating().doubleValue());
					item.put("resolved_cases", l.getResolvedCases());
					item.put("years_experience", l.getYearsExperience());
					return item;
				})
				.toList();

		// ── Intento ML ──
		try {
			JsonNode mlData = mlProxyService.recommendations(doctorId.toString(), profilePayload, TOP_K, lawyerCorpus);

			List<RecommendationRunService.ComputedItem> items = new ArrayList<>();
			String modelVersion = null;
			if (mlData != null && mlData.has("recommendations")) {
				for (JsonNode rec : mlData.get("recommendations")) {
					UUID lawyerUserId = UUID.fromString(rec.get("lawyer_id").asText());
					LawyerProfile lawyer = byUserId.get(lawyerUserId);
					if (lawyer == null) {
						continue;
					}
					// H-03: campos obligatorios del modelo compuesto -- si falta uno o esta
					// fuera de rango, se lanza y el catch de mas abajo activa el fallback
					// identificado en vez de una explicacion numerica falsa.
					BigDecimal scoreRaw = requiredNormalizedScore(rec, "score");
					BigDecimal contentScoreRaw = requiredNormalizedScore(rec, "content_score");
					BigDecimal performanceScoreRaw = requiredNormalizedScore(rec, "performance_score");
					modelVersion = rec.path("model_used").asText(modelVersion);

					items.add(new RecommendationRunService.ComputedItem(
							lawyerUserId, scoreRaw, contentScoreRaw, performanceScoreRaw,
							toStringList(rec.path("matched_specialties")),
							rec.path("feature_importance"),
							toStringList(rec.path("reasons")),
							objectMapper.valueToTree(LawyerCardDto.from(lawyer))));
				}
			}

			Map<String, Object> modelInfo = mlData != null && mlData.has("model_info")
					? objectMapper.convertValue(mlData.get("model_info"), MAP_TYPE)
					: Map.of();
			@SuppressWarnings("unchecked")
			Map<String, Object> weights = modelInfo.get("weights") instanceof Map<?, ?> w
					? (Map<String, Object>) w
					: Map.of();

			return new ComputedBundle(items, "ml", modelVersion, weights, modelInfo, profilePayload, lawyerCorpus,
					lawyers.size());
		} catch (Exception ex) {
			log.info("ML no disponible para matching ({}); usando fallback por especialidad", ex.getMessage());
		}

		// ── Fallback cold-start: matching por specialty ↔ medical_areas ──
		// Score determinístico (sin azar): base por coincidencia de área + señales
		// de desempeño (rating y casos resueltos), mismo espíritu que el score
		// compuesto del ML service.
		String specialtyLower = specialty.toLowerCase();
		List<RecommendationRunService.ComputedItem> fallback = lawyers.stream()
				.filter(l -> l.getMedicalAreas().stream().anyMatch(area ->
						area.toLowerCase().contains(specialtyLower) || specialtyLower.contains(area.toLowerCase())))
				.map(l -> {
					int score = fallbackScore(l);
					// H-03: el fallback (60 base + rating + casos) no descompone en
					// contenido/desempeño como el modelo compuesto -- esos raw quedan
					// null en vez de fingir una formula que no corrio.
					BigDecimal scoreRaw = BigDecimal.valueOf(score)
							.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
					List<String> reasons = List.of("Coincidencia por área médica (sin ML service)",
							String.format("Valoración %.1f/5 · %d casos resueltos",
									l.getRating() == null ? 0.0 : l.getRating().doubleValue(),
									l.getResolvedCases()));
					return new RecommendationRunService.ComputedItem(
							l.getUser().getId(), scoreRaw, null, null, List.of(),
							objectMapper.createArrayNode(), reasons,
							objectMapper.valueToTree(LawyerCardDto.from(l)));
				})
				.sorted((a, b) -> b.scoreRaw().compareTo(a.scoreRaw()))
				.toList();

		return new ComputedBundle(fallback, "fallback", null, Map.of(),
				Map.of("model", "fallback", "message", "ML service no disponible"),
				profilePayload, lawyerCorpus, lawyers.size());
	}

	/**
	 * Score de respaldo determinístico: 60 base por coincidencia de área +
	 * hasta 25 por rating + hasta 15 por casos resueltos (saturado en 50).
	 */
	private static int fallbackScore(LawyerProfile lawyer) {
		double rating = lawyer.getRating() == null ? 0.0 : lawyer.getRating().doubleValue();
		double score = 60 + (rating / 5.0) * 25 + Math.min(lawyer.getResolvedCases(), 50) / 50.0 * 15;
		return (int) Math.round(Math.min(score, 100));
	}

	// ── Mapeo a DTOs desde entidades PERSISTIDAS (IDs reales) ──────────────────

	private RecommendationDto toRecommendationDto(MatchRecommendation entity, RecommendationRun run) {
		LawyerCardDto lawyer = lawyerCardFromSnapshot(entity);
		return new RecommendationDto(
				entity.getId().toString(),
				run.getDoctor().getId().toString(),
				lawyer,
				entity.getScore().setScale(0, RoundingMode.HALF_UP).intValue(),
				toPercentOrZero(entity.getContentScoreRaw()),
				toPercentOrZero(entity.getPerformanceScoreRaw()),
				0,
				entity.getScoreRaw(),
				entity.getContentScoreRaw(),
				entity.getPerformanceScoreRaw(),
				entity.getMatchedSpecialties() == null ? List.of() : entity.getMatchedSpecialties(),
				entity.getAlgorithmVersion(),
				parseFactors(entity.getFactors()),
				entity.getReasons(),
				entity.getCreatedAt().toString());
	}

	private LawyerCardDto lawyerCardFromSnapshot(MatchRecommendation entity) {
		if (entity.getLawyerSnapshot() != null) {
			try {
				return objectMapper.treeToValue(entity.getLawyerSnapshot(), LawyerCardDto.class);
			} catch (Exception ex) {
				log.warn("No se pudo leer lawyer_snapshot de la recomendación {}: {}", entity.getId(), ex.getMessage());
			}
		}
		// Fotografía ausente (fila legacy o error de serialización): se reconstruye
		// desde el perfil VIVO como último recurso, nunca se inventa una tarjeta.
		return lawyerProfileRepository.findById(entity.getLawyer().getId())
				.map(LawyerCardDto::from)
				.orElse(null);
	}

	private static RecommendationRunSummaryDto toSummaryDto(RecommendationRun run) {
		return new RecommendationRunSummaryDto(
				run.getId().toString(),
				run.getLegalCase().getId().toString(),
				run.getStatus().getValue(),
				run.getOrigin(),
				run.getModelVersion(),
				run.getPipelineVersion(),
				run.getCreatedAt(),
				run.getCompletedAt(),
				run.getCandidateCount(),
				run.getResultCount());
	}

	private RecommendationRunDto toRunDto(RecommendationRun run, List<MatchRecommendation> rows) {
		List<RecommendationDto> recs = rows.stream().map(r -> toRecommendationDto(r, run)).toList();
		return new RecommendationRunDto(
				run.getId().toString(),
				run.getLegalCase().getId().toString(),
				run.getStatus().getValue(),
				run.getOrigin(),
				run.getModelVersion(),
				run.getPipelineVersion(),
				run.getCreatedAt(),
				run.getCompletedAt(),
				toMap(run.getWeights()),
				recs,
				RecommendationRunDto.ADVISORY_NOTE);
	}

	// ── Autorización ─────────────────────────────────────────────────────────

	private LegalCase loadCaseForMatchingView(AuthenticatedUser user, String caseIdParam) {
		if (caseIdParam == null || caseIdParam.isBlank()) {
			throw new BadRequestException("caseId es requerido");
		}
		LegalCase legalCase = caseRepository.findWithPeopleById(parseUuid(caseIdParam, "caseId"))
				.orElseThrow(() -> new NotFoundException("Caso no encontrado"));
		assertCanViewMatching(user, legalCase);
		return legalCase;
	}

	/** Médico propietario, administrador, o abogado efectivamente asignado al caso (mismos permisos que el detalle). */
	private static void assertCanViewMatching(AuthenticatedUser user, LegalCase legalCase) {
		boolean allowed = switch (user.role()) {
			case DOCTOR -> legalCase.getDoctor().getId().equals(user.id());
			case LAWYER -> legalCase.getLawyer() != null && legalCase.getLawyer().getId().equals(user.id());
			case ADMIN -> true;
		};
		if (!allowed) {
			throw new ForbiddenException("No tienes permisos para ver el matching de esta consulta");
		}
	}

	private LegalCase resolveCaseForDoctor(AuthenticatedUser user, UUID doctorId, String caseIdParam) {
		LegalCase legalCase = caseRepository.findWithPeopleById(parseUuid(caseIdParam, "caseId"))
				.orElseThrow(() -> new NotFoundException("Caso no encontrado"));
		if (!legalCase.getDoctor().getId().equals(doctorId)) {
			throw new ForbiddenException("No puedes consultar recomendaciones de una consulta ajena");
		}
		if (user.role() == UserRole.DOCTOR && !doctorId.equals(user.id())) {
			throw new ForbiddenException("No puedes consultar recomendaciones de otra consulta");
		}
		assertCanViewMatching(user, legalCase);
		return legalCase;
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

	// ── Helpers ─────────────────────────────────────────────────────────────

	/** Texto del caso para el vector TF-IDF: título + descripción + tipo de evento + especialidad. */
	private static String buildCaseText(LegalCase legalCase) {
		if (legalCase == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		if (legalCase.getTitle() != null) sb.append(legalCase.getTitle()).append(' ');
		if (legalCase.getDescription() != null) sb.append(legalCase.getDescription()).append(' ');
		if (legalCase.getEventType() != null) sb.append(legalCase.getEventType()).append(' ');
		if (legalCase.getMedicalSpecialty() != null) sb.append(legalCase.getMedicalSpecialty());
		return sb.toString().strip();
	}

	/**
	 * H-03: extrae un componente normalizado [0,1] del modelo compuesto, validando
	 * presencia/rango/finitud en vez de {@code path(...).asDouble(0)} -- un campo
	 * faltante o fuera de rango no debe convertirse silenciosamente en 0.
	 */
	private static BigDecimal requiredNormalizedScore(JsonNode rec, String field) {
		JsonNode node = rec.get(field);
		if (node == null || !node.isNumber()) {
			throw new IllegalStateException(
					"Respuesta ML inválida: falta o no es numérico el campo '" + field + "'");
		}
		double value = node.asDouble();
		if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
			throw new IllegalStateException(
					"Respuesta ML inválida: '" + field + "'=" + value + " fuera de rango [0,1]");
		}
		return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
	}

	private static int toPercentOrZero(BigDecimal raw) {
		return raw == null ? 0 : raw.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
	}

	private static List<String> toStringList(JsonNode node) {
		List<String> list = new ArrayList<>();
		if (node != null && node.isArray()) {
			node.forEach(item -> list.add(item.asText()));
		}
		return list;
	}

	private JsonNode parseFactors(String factorsJson) {
		try {
			return objectMapper.readTree(factorsJson == null ? "[]" : factorsJson);
		} catch (Exception ex) {
			return objectMapper.createArrayNode();
		}
	}

	private Map<String, Object> toMap(JsonNode node) {
		if (node == null || node.isNull()) {
			return new LinkedHashMap<>();
		}
		return objectMapper.convertValue(node, MAP_TYPE);
	}

	private String toJsonString(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			return String.valueOf(value);
		}
	}

	private static UUID parseUuid(String value, String field) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException(field + " inválido");
		}
	}

	private static String sanitizeError(String message) {
		if (message == null) {
			return "Error desconocido";
		}
		// No registrar HTML/stacktraces/datos clinicos en el mensaje de fallo expuesto.
		String trimmed = message.strip();
		return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
	}

	private static String sha256(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 no disponible", ex);
		}
	}
}
