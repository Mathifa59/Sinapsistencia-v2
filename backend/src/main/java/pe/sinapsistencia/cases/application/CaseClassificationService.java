package pe.sinapsistencia.cases.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.sinapsistencia.cases.domain.CaseContext;
import pe.sinapsistencia.cases.domain.CasePriority;
import pe.sinapsistencia.cases.domain.CaseStatus;
import pe.sinapsistencia.cases.domain.LegalCase;
import pe.sinapsistencia.cases.infrastructure.CaseEventRepository;
import pe.sinapsistencia.cases.infrastructure.LegalCaseRepository;
import pe.sinapsistencia.config.ClockConfig;
import pe.sinapsistencia.ml.application.MlProxyService;
import pe.sinapsistencia.ml.domain.CaseComplexity;
import pe.sinapsistencia.ml.domain.MlClassification;
import pe.sinapsistencia.ml.infrastructure.MlClassificationRepository;
import pe.sinapsistencia.notifications.MailTemplates;
import pe.sinapsistencia.notifications.NotificationService;

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
 * Riesgo alto/crítico dispara la alerta automática por correo. Si el ML no responde,
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
	/** H-05: version del pipeline de INTEGRACION (que se envia al modelo), no del modelo en si. */
	private static final String PIPELINE_VERSION = "risk-input-pipeline-v3";

	private final MlProxyService mlProxyService;
	private final NotificationService notificationService;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final String riskAlertTo;

	public CaseClassificationService(MlClassificationRepository classificationRepository,
			CaseEventRepository eventRepository,
			LegalCaseRepository caseRepository,
			MlProxyService mlProxyService,
			NotificationService notificationService,
			ObjectMapper objectMapper,
			Clock clock,
			@Value("${app.resend.risk-alert-to:}") String riskAlertTo) {
		this.classificationRepository = classificationRepository;
		this.eventRepository = eventRepository;
		this.caseRepository = caseRepository;
		this.mlProxyService = mlProxyService;
		this.notificationService = notificationService;
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.riskAlertTo = riskAlertTo == null ? "" : riskAlertTo.strip();
	}

	@Transactional
	public MlClassification classifyAndPrioritize(LegalCase legalCase, CaseContext context) {
		long start = System.currentTimeMillis();

		CasePriority perceived = legalCase.getPerceivedUrgency() != null
				? legalCase.getPerceivedUrgency()
				: legalCase.getPriority();

		// H-05: la complejidad reportada por el médico (independiente de la urgencia)
		// manda sobre la derivación legacy. Un caso histórico/legacy sin el campo
		// se deriva como antes, pero queda marcado para no confundirse con un dato real.
		CaseComplexity complexity;
		String complexitySource;
		if (legalCase.getProcedureComplexity() != null) {
			complexity = legalCase.getProcedureComplexity();
			complexitySource = legalCase.getComplexitySource() != null
					? legalCase.getComplexitySource()
					: "reported";
		} else {
			complexity = deriveComplexity(perceived);
			complexitySource = "inferred_from_urgency_legacy";
			legalCase.setProcedureComplexity(complexity);
			legalCase.setComplexitySource(complexitySource);
		}

		String caseType = deriveCaseType(legalCase);
		String suggestedSpecialty = deriveSuggestedSpecialty(legalCase);
		String specialty = resolveSpecialty(legalCase, context);

		MlClassification classification = new MlClassification(legalCase);
		classification.setCaseType(caseType);
		classification.setComplexity(complexity);
		classification.setSuggestedSpecialty(suggestedSpecialty);
		classification.setPipelineVersion(PIPELINE_VERSION);

		// H-05: las 7 variables del RF se arman UNA vez — son la fuente de verdad
		// tanto para la llamada al ML como para el snapshot que se persiste.
		Map<String, Object> mlInputs = new LinkedHashMap<>();
		mlInputs.put("specialty", specialty);
		mlInputs.put("procedure_complexity", complexity.getValue());
		mlInputs.put("priority", perceived.getValue());
		mlInputs.put("documentation_complete", legalCase.isDocumentationComplete());
		mlInputs.put("informed_consent", legalCase.isInformedConsent());
		mlInputs.put("has_prior_complaints", legalCase.isHasPriorComplaints());
		Long daysSince = daysSinceEvent(context);
		if (daysSince != null) {
			mlInputs.put("time_since_incident_days", daysSince);
		}
		classification.setInputSnapshot(buildInputSnapshot(mlInputs, complexitySource, context));

		// ── Intento con el Random Forest real ──────────────────────────────────
		Map<String, Object> risk = null;
		try {
			Map<String, Object> payload = new LinkedHashMap<>(mlInputs);
			payload.put("case_id", legalCase.getId().toString());
			payload.put("description", legalCase.getDescription() == null ? "" : legalCase.getDescription());
			risk = mlProxyService.riskAssessment(payload);
		} catch (Exception ex) {
			log.info("ML no disponible para clasificar el caso {} ({}); fallback por reglas",
					legalCase.getId(), ex.getMessage());
		}

		CasePriority finalPriority;
		String justification;
		Map<String, Object> pendingAlert = null;
		String pendingAlertLevel = null;

		if (risk != null && !isValidMlRisk(risk)) {
			log.warn("Respuesta ML inválida al clasificar el caso {}: {} — se trata como fallo del servicio",
					legalCase.getId(), risk);
			risk = null;
		}

		if (risk != null) {
			String riskLevel = String.valueOf(risk.get("riskLevel"));
			double riskScore = ((Number) risk.get("riskScore")).doubleValue();
			String modelVersion = String.valueOf(risk.get("modelVersion"));
			CasePriority suggested = RISK_TO_PRIORITY.get(riskLevel);

			finalPriority = suggested;
			classification.setUrgency(suggested);
			classification.setRiskLevel(riskLevel);
			classification.setRiskScore(BigDecimal.valueOf(riskScore).setScale(4, RoundingMode.HALF_UP));
			classification.setRiskFactors(toJson(risk.get("riskFactors")));
			classification.setModelVersion(modelVersion);

			justification = String.format(Locale.ROOT,
					"Random Forest %s: score de riesgo %.2f%% (nivel %s) → prioridad sugerida '%s'. "
							+ "Urgencia percibida por el médico: '%s'.",
					modelVersion, riskScore * 100, riskLevel, suggested.getValue(), perceived.getValue());

			// HU-31: riesgo alto/crítico dispara la alerta automática por correo.
			// H-06: el encolado se difiere hasta después de guardar la clasificación
			// (necesita un classificationId real y estable para el outbox).
			if ("alto".equals(riskLevel) || "critico".equals(riskLevel)) {
				pendingAlert = new LinkedHashMap<>();
				pendingAlert.put("caseId", legalCase.getId().toString());
				pendingAlert.put("riskScore", riskScore);
				pendingAlert.put("riskLevel", riskLevel);
				pendingAlert.put("riskFactors", risk.get("riskFactors"));
				pendingAlert.put("recommendations", risk.get("recommendations"));
				pendingAlert.put("specialty", specialty);
				pendingAlert.put("doctorName", legalCase.getDoctor().getName());
				pendingAlert.put("doctorEmail", legalCase.getDoctor().getEmail());
				pendingAlert.put("documentationComplete", legalCase.isDocumentationComplete());
				pendingAlert.put("informedConsent", legalCase.isInformedConsent());
				pendingAlert.put("evaluatedAt", Instant.now(clock).toString());
				pendingAlertLevel = riskLevel;
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

		if (pendingAlert != null) {
			notificationService.enqueue(
					"risk_alert:" + classification.getId(),
					"risk_alert",
					legalCase.getId(),
					"ml_classification", classification.getId(),
					riskAlertTo, null,
					"Alerta de riesgo " + pendingAlertLevel + " — Sinapsistencia",
					MailTemplates.riskAlert(pendingAlert));
		}

		return classification;
	}

	/**
	 * H-04: valida presencia/rango/finitud de la respuesta del Random Forest antes
	 * de confiarla. Un campo faltante o fuera de rango no debe convertirse
	 * silenciosamente en 0 (riskScore) ni en un nivel inventado — se trata como
	 * fallo del servicio y cae al fallback {@code rules-v1} ya existente.
	 */
	/** Visibilidad de paquete (no private) a propósito: probado directamente en {@code CaseClassificationServiceTest}. */
	static boolean isValidMlRisk(Map<String, Object> risk) {
		Object riskLevel = risk.get("riskLevel");
		if (!(riskLevel instanceof String level) || !RISK_TO_PRIORITY.containsKey(level)) {
			return false;
		}
		Object riskScore = risk.get("riskScore");
		if (!(riskScore instanceof Number scoreNumber)) {
			return false;
		}
		double score = scoreNumber.doubleValue();
		if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
			return false;
		}
		Object modelVersion = risk.get("modelVersion");
		return modelVersion instanceof String mv && !mv.isBlank();
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value == null ? java.util.List.of() : value);
		} catch (Exception ex) {
			return "[]";
		}
	}

	/**
	 * H-05: fotografía de las 7 variables enviadas al RF + metadata de contexto.
	 * Se guarda SIEMPRE (ML disponible o no) porque documenta qué se evaluó,
	 * no qué respondió el modelo.
	 */
	private JsonNode buildInputSnapshot(Map<String, Object> mlInputs, String complexitySource, CaseContext context) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("inputs", mlInputs);
		snapshot.put("complexitySource", complexitySource);
		snapshot.put("evaluatedAt", Instant.now(clock).toString());
		snapshot.put("eventDate", context != null && context.getEventDate() != null
				? context.getEventDate().toString()
				: null);
		snapshot.put("timeZone", ClockConfig.BUSINESS_ZONE.getId());
		return objectMapper.valueToTree(snapshot);
	}

	private Long daysSinceEvent(CaseContext context) {
		if (context == null || context.getEventDate() == null) {
			return null;
		}
		LocalDate eventDate = context.getEventDate();
		long days = ChronoUnit.DAYS.between(eventDate, LocalDate.now(clock));
		return Math.max(days, 0);
	}

	/** Visibilidad de paquete a propósito: reutilizada por {@code CaseWorkflowService} para el cálculo de isStale (H-05). */
	static String resolveSpecialty(LegalCase legalCase, CaseContext context) {
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
