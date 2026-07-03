package pe.sinapsistencia.cases.application;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.sinapsistencia.cases.domain.CasePriority;
import pe.sinapsistencia.cases.domain.CaseStatus;
import pe.sinapsistencia.cases.domain.LegalCase;
import pe.sinapsistencia.cases.infrastructure.CaseEventRepository;
import pe.sinapsistencia.cases.infrastructure.LegalCaseRepository;
import pe.sinapsistencia.ml.domain.CaseComplexity;
import pe.sinapsistencia.ml.domain.MlClassification;
import pe.sinapsistencia.ml.infrastructure.MlClassificationRepository;

/** Clasificación y priorización ML de consultas (HU-29, HU-30). */
@Service
public class CaseClassificationService {

	private final MlClassificationRepository classificationRepository;
	private final CaseEventRepository eventRepository;
	private final LegalCaseRepository caseRepository;

	public CaseClassificationService(MlClassificationRepository classificationRepository,
			CaseEventRepository eventRepository,
			LegalCaseRepository caseRepository) {
		this.classificationRepository = classificationRepository;
		this.eventRepository = eventRepository;
		this.caseRepository = caseRepository;
	}

	@Transactional
	public MlClassification classifyAndPrioritize(LegalCase legalCase) {
		long start = System.currentTimeMillis();

		CasePriority urgency = legalCase.getPerceivedUrgency() != null
				? legalCase.getPerceivedUrgency()
				: legalCase.getPriority();
		CaseComplexity complexity = deriveComplexity(legalCase);
		String caseType = deriveCaseType(legalCase);
		String suggestedSpecialty = deriveSuggestedSpecialty(legalCase);

		MlClassification classification = new MlClassification(legalCase);
		classification.setCaseType(caseType);
		classification.setUrgency(urgency);
		classification.setComplexity(complexity);
		classification.setSuggestedSpecialty(suggestedSpecialty);
		classification.setConfidence(new BigDecimal("0.8500"));
		classification.setModelVersion("rules-v1");
		classification.setResponseTimeMs((int) (System.currentTimeMillis() - start));
		classification = classificationRepository.save(classification);

		String justification = String.format(
				Locale.ROOT,
				"Clasificación automática: tipo=%s, urgencia=%s, complejidad=%s, especialidad sugerida=%s (confianza %.0f%%)",
				caseType,
				urgency.getValue(),
				complexity.getValue(),
				suggestedSpecialty,
				classification.getConfidence().doubleValue() * 100);

		legalCase.setPriority(urgency);
		legalCase.setPriorityJustification(justification);
		if (legalCase.getStatus() == CaseStatus.PENDIENTE) {
			legalCase.setStatus(CaseStatus.CLASIFICADA);
		}
		caseRepository.save(legalCase);

		CaseWorkflowService.recordSystemEvent(eventRepository, legalCase, legalCase.getDoctor(),
				"clasificacion_ml",
				"Consulta clasificada por el sistema: " + justification);

		return classification;
	}

	private static CaseComplexity deriveComplexity(LegalCase legalCase) {
		return switch (legalCase.getPriority()) {
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
