package pe.sinapsistencia.matching.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.sinapsistencia.auth.domain.UserRole;
import pe.sinapsistencia.auth.infrastructure.ProfileRepository;
import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.cases.domain.CaseStatus;
import pe.sinapsistencia.cases.domain.LegalCase;
import pe.sinapsistencia.cases.infrastructure.LegalCaseRepository;
import pe.sinapsistencia.matching.domain.MatchRecommendation;
import pe.sinapsistencia.matching.infrastructure.MatchRecommendationRepository;
import pe.sinapsistencia.matching.web.dto.LawyerCardDto;
import pe.sinapsistencia.matching.web.dto.RecommendationDto;
import pe.sinapsistencia.matching.web.dto.RecommendationDto.RecommendationsResponse;
import pe.sinapsistencia.ml.application.MlProxyService;
import pe.sinapsistencia.profile.domain.DoctorProfile;
import pe.sinapsistencia.profile.domain.LawyerProfile;
import pe.sinapsistencia.profile.infrastructure.DoctorProfileRepository;
import pe.sinapsistencia.profile.infrastructure.LawyerProfileRepository;
import pe.sinapsistencia.shared.exception.ForbiddenException;
import pe.sinapsistencia.shared.exception.NotFoundException;

/**
 * Recomendaciones médico↔abogado vía ML (TF-IDF + coseno, HU-31/32) con
 * persistencia de factores XAI y fallback de cold-start por especialidad.
 */
@Service
public class RecommendationService {

	private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

	private final DoctorProfileRepository doctorProfileRepository;
	private final LawyerProfileRepository lawyerProfileRepository;
	private final ProfileRepository profileRepository;
	private final MatchRecommendationRepository recommendationRepository;
	private final LegalCaseRepository caseRepository;
	private final MlProxyService mlProxyService;
	private final ObjectMapper objectMapper;
	private final Random random = new Random();

	public RecommendationService(DoctorProfileRepository doctorProfileRepository,
			LawyerProfileRepository lawyerProfileRepository,
			ProfileRepository profileRepository,
			MatchRecommendationRepository recommendationRepository,
			LegalCaseRepository caseRepository,
			MlProxyService mlProxyService,
			ObjectMapper objectMapper) {
		this.doctorProfileRepository = doctorProfileRepository;
		this.lawyerProfileRepository = lawyerProfileRepository;
		this.profileRepository = profileRepository;
		this.recommendationRepository = recommendationRepository;
		this.caseRepository = caseRepository;
		this.mlProxyService = mlProxyService;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public RecommendationsResponse recommendations(AuthenticatedUser user, String doctorIdParam) {
		UUID doctorId = resolveDoctor(user, doctorIdParam);
		return computeRecommendations(doctorId);
	}

	/** POST: calcula recomendaciones (para una consulta opcional) y las PERSISTE con factores XAI. */
	@Transactional
	public RecommendationsResponse generateAndPersist(AuthenticatedUser user, String caseIdParam) {
		if (user.role() != UserRole.DOCTOR) {
			throw new ForbiddenException("Solo un médico puede generar recomendaciones");
		}
		UUID doctorId = user.id();

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

	private static List<String> toStringList(JsonNode node) {
		List<String> list = new ArrayList<>();
		if (node != null && node.isArray()) {
			node.forEach(item -> list.add(item.asText()));
		}
		return list;
	}
}
