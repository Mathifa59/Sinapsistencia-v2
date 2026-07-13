package pe.sinapsistencia.cases.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import pe.sinapsistencia.cases.domain.CaseContext;
import pe.sinapsistencia.cases.domain.CasePriority;
import pe.sinapsistencia.cases.domain.CaseStatus;
import pe.sinapsistencia.cases.domain.LegalCase;
import pe.sinapsistencia.cases.infrastructure.CaseEventRepository;
import pe.sinapsistencia.cases.infrastructure.LegalCaseRepository;
import pe.sinapsistencia.ml.application.MlProxyService;
import pe.sinapsistencia.ml.application.N8nNotifier;
import pe.sinapsistencia.ml.domain.CaseComplexity;
import pe.sinapsistencia.ml.domain.MlClassification;
import pe.sinapsistencia.ml.infrastructure.MlClassificationRepository;

/**
 * Clasificación y priorización de casos (HU-29/30/31) — pipeline UNIFICADO.
 *
 * Al crear un caso, este servicio llama al Random Forest real del ML service
 * (/api/v1/risk-assessment) con los factores del caso y PERSISTE el resultado
 * (score, nivel, desglose de factores, versión del modelo) en
 * ml_classifications: lo que el médico ve en la animación de creación es
 * exactamente lo que muestra el detalle del caso — una sola fuente de verdad.
 *
 * La prioridad del caso pasa a ser la SUGERIDA por el modelo (nivel de riesgo
 * → prioridad); la urgencia percibida del médico queda documentada en la
 * justificación y puede imponerse editando el caso (HU-43: apoyo, no decisión).
 * Riesgo alto/crítico dispara la alerta automática n8n. Si el ML no responde,
 * se degrada al sistema de reglas (rules-v1) usando la urgencia percibida.
 */
@Service
public class CaseClassificationService {

	private static final Logger log = LoggerFactory.getLogger(CaseClassificationService.class);

	private static final Map<String, CasePriority> RISK_TO_PRIORITY = Map.of(
			"bajo", CasePriority.BAJA,
			"moderado", CasePriority.MEDIA,
			"alto", CasePriority.ALTA,
			"critico", CasePriority.CRITICA);

	private final MlClassificationRepository classificationRepository;
	private final CaseEventRepository eventRepository;
	private final LegalCaseRepository caseRepository;
	private final MlProxyService mlProxyService;
	private final N8nNotifier n8nNotifier;
	private final ObjectMapper objectMapper;

	public CaseClassificationService(MlClassificationRepository classificationRepository,
			CaseEventRepository eventRepository,
			LegalCaseRepository caseRepository,
			MlProxyService mlProxyService,
			N8nNotifier n8nNotifier,
			ObjectMapper objectMapper) {
		this.classificationRepository = classificationRepository;
		this.eventRepository = eventRepository;
		this.caseRepository = caseRepository;
		this.mlProxyService = mlProxyService;
		this.n8nNotifier = n8nNotifier;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public MlClassification classifyAndPrioritize(LegalCase legalCase, CaseContext context) {
		long start = System.currentTimeMillis();

		CasePriority perceived = legalCase.getPerceivedUrgency() != null
				? legalCase.getPerceivedUrgency()
				: legalCase.getPriority();
		CaseComplexity complexity = deriveComplexity(perceived);
		String caseType = deriveCaseType(legalCase);
		String suggestedSpecialty = deriveSuggestedSpecialty(legalCase);
		String specialty = resolveSpecialty(legalCase, context);

		MlClassification classification = new MlClassification(legalCase);
		classification.setCaseType(caseType);
		classification.setComplexity(complexity);
		classification.setSuggestedSpecialty(suggestedSpecialty);

		// ── Intento con el Random Forest real ──────────────────────────────────
		Map<String, Object> risk = null;
		try {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("case_id", legalCase.getId().toString());
			payload.put("specialty", specialty);
			payload.put("procedure_complexity", complexity.getValue());
			payload.put("priority", perceived.getValue());
			payload.put("documentation_complete", legalCase.isDocumentationComplete());
			payload.put("informed_consent", legalCase.isInformedConsent());
			payload.put("has_prior_complaints", legalCase.isHasPriorComplaints());
			Long daysSince = daysSinceEvent(context);
			if (daysSince != null) {
				payload.put("time_since_incident_days", daysSince);
			}
			payload.put("description", legalCase.getDescription() == null ? "" : legalCase.getDescription());
			risk = mlProxyService.riskAssessment(payload);
		} catch (Exception ex) {
			log.info("ML no disponible para clasificar el caso {} ({}); fallback por reglas",
					legalCase.getId(), ex.getMessage());
		}

		CasePriority finalPriority;
		String justification;

		if (risk != null && risk.get("riskLevel") != null) {
			String riskLevel = String.valueOf(risk.get("riskLevel"));
			double riskScore = risk.get("riskScore") instanceof Number n ? n.doubleValue() : 0.0;
			String modelVersion = String.valueOf(risk.getOrDefault("modelVersion", "rf"));
			CasePriority suggested = RISK_TO_PRIORITY.getOrDefault(riskLevel, perceived);

			finalPriority = suggested;
			classification.setUrgency(suggested);
			classification.setRiskLevel(riskLevel);
			classification.setRiskScore(BigDecimal.valueOf(riskScore).setScale(4, RoundingMode.HALF_UP));
			classification.setRiskFactors(toJson(risk.get("riskFactors")));
			classification.setModelVersion(modelVersion);

			justification = String.format(Locale.ROOT,
					"Random Forest %s: score de riesgo %.0f%% (nivel %s) → prioridad sugerida '%s'. "
							+ "Urgencia percibida por el médico: '%s'.",
					modelVersion, riskScore * 100, riskLevel, suggested.getValue(), perceived.getValue());

			// HU-31: riesgo alto/crítico dispara la alerta automática (n8n).
			if ("alto".equals(riskLevel) || "critico".equals(riskLevel)) {
				Map<String, Object> alert = new LinkedHashMap<>();
				alert.put("caseId", legalCase.getId().toString());
				alert.put("riskScore", riskScore);
				alert.put("riskLevel", riskLevel);
				alert.put("riskFactors", risk.get("riskFactors"));
				alert.put("recommendations", risk.get("recommendations"));
				alert.put("specialty", specialty);
				alert.put("doctorName", legalCase.getDoctor().getName());
				alert.put("doctorEmail", legalCase.getDoctor().getEmail());
				alert.put("documentationComplete", legalCase.isDocumentationComplete());
				alert.put("informedConsent", legalCase.isInformedConsent());
				alert.put("evaluatedAt", Instant.now().toString());
				n8nNotifier.triggerRiskAlert(alert);
			}
		} else {
			// ── Fallback por reglas (ML caído): la urgencia percibida manda ────
			finalPriority = perceived;
			classification.setUrgency(perceived);
			classification.setModelVersion("rules-v1");
			justification = String.format(Locale.ROOT,
					"Clasificación por reglas de respaldo (servicio ML no disponible): "
							+ "prioridad tomada de la urgencia percibida '%s', complejidad %s.",
					perceived.getValue(), complexity.getValue());
		}

		classification.setResponseTimeMs((int) (System.currentTimeMillis() - start));
		classification = classificationRepository.save(classification);

		legalCase.setPriority(finalPriority);
		legalCase.setPriorityJustification(justification);
		if (legalCase.getStatus() == CaseStatus.PENDIENTE) {
			legalCase.setStatus(CaseStatus.CLASIFICADA);
		}
		caseRepository.save(legalCase);

		CaseWorkflowService.recordSystemEvent(eventRepository, legalCase, legalCase.getDoctor(),
				"clasificacion_ml",
				"Caso clasificado por el sistema: " + justification);

		return classification;
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value == null ? java.util.List.of() : value);
		} catch (Exception ex) {
			return "[]";
		}
	}

	private static Long daysSinceEvent(CaseContext context) {
		if (context == null || context.getEventDate() == null) {
			return null;
		}
		LocalDate eventDate = context.getEventDate();
		long days = ChronoUnit.DAYS.between(eventDate, LocalDate.now());
		return Math.max(days, 0);
	}

	private static String resolveSpecialty(LegalCase legalCase, CaseContext context) {
		if (legalCase.getMedicalSpecialty() != null && !legalCase.getMedicalSpecialty().isBlank()) {
			return legalCase.getMedicalSpecialty();
		}
		if (context != null && context.getMedicalArea() != null && !context.getMedicalArea().isBlank()) {
			return context.getMedicalArea();
		}
		return "Medicina General";
	}

	private static CaseComplexity deriveComplexity(CasePriority priority) {
		return switch (priority) {
			case CRITICA, ALTA -> CaseComplexity.ALTA;
			case MEDIA -> CaseComplexity.MEDIA;
			case BAJA -> CaseComplexity.BAJA;
		};
	}

	private static String deriveCaseType(LegalCase legalCase) {
		if (legalCase.getEventType() != null && !legalCase.getEventType().isBlank()) {
			return legalCase.getEventType();
		}
		return "consulta_medico_legal";
	}

	private static String deriveSuggestedSpecialty(LegalCase legalCase) {
		String specialty = legalCase.getMedicalSpecialty();
		if (specialty != null && (specialty.toLowerCase(Locale.ROOT).contains("cirug")
				|| specialty.toLowerCase(Locale.ROOT).contains("trauma"))) {
			return "Responsabilidad Civil Médica";
		}
		return "Derecho Médico";
	}
}
