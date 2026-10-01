package pe.sinapsistencia.cases.application;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import pe.sinapsistencia.auth.domain.Profile;
import pe.sinapsistencia.auth.domain.UserRole;
import pe.sinapsistencia.cases.domain.CaseContext;
import pe.sinapsistencia.cases.domain.CasePriority;
import pe.sinapsistencia.cases.domain.LegalCase;
import pe.sinapsistencia.ml.domain.CaseComplexity;
import pe.sinapsistencia.ml.domain.MlClassification;

/**
 * H-05: {@code computeIsStale} nunca recalcula con el reloj del navegador --
 * solo compara el estado actual del caso contra la fotografía persistida.
 */
class CaseWorkflowServiceTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static LegalCase legalCase() {
		Profile doctor = new Profile("doctor@sanna.pe", "Dra. Prueba", UserRole.DOCTOR, "hash");
		LegalCase legalCase = new LegalCase("Título", "Descripción de prueba con largo suficiente", doctor);
		legalCase.setPriority(CasePriority.MEDIA);
		legalCase.setPerceivedUrgency(CasePriority.ALTA);
		legalCase.setMedicalSpecialty("Psiquiatría");
		legalCase.setProcedureComplexity(CaseComplexity.BAJA);
		legalCase.setComplexitySource("reported");
		legalCase.setDocumentationComplete(true);
		legalCase.setInformedConsent(true);
		legalCase.setHasPriorComplaints(false);
		return legalCase;
	}

	private static MlClassification classificationWithSnapshot(LegalCase legalCase, String eventDate) {
		MlClassification classification = new MlClassification(legalCase);
		JsonNode inputs = MAPPER.createObjectNode()
				.put("specialty", "Psiquiatría")
				.put("procedure_complexity", "baja")
				.put("priority", "alta")
				.put("documentation_complete", true)
				.put("informed_consent", true)
				.put("has_prior_complaints", false)
				.put("time_since_incident_days", 0);
		var snapshot = MAPPER.createObjectNode();
		snapshot.set("inputs", inputs);
		snapshot.put("complexitySource", "reported");
		if (eventDate != null) {
			snapshot.put("eventDate", eventDate);
		} else {
			snapshot.putNull("eventDate");
		}
		classification.setInputSnapshot(snapshot);
		return classification;
	}

	@Test
	void devuelveNuloCuandoLaFotografiaEsLegacy() {
		MlClassification classification = new MlClassification(legalCase());
		assertNull(CaseWorkflowService.computeIsStale(legalCase(), null, classification));
	}

	@Test
	void noEsObsoletaCuandoNadaCambio() {
		LegalCase legalCase = legalCase();
		MlClassification classification = classificationWithSnapshot(legalCase, null);
		assertFalse(CaseWorkflowService.computeIsStale(legalCase, null, classification));
	}

	@Test
	void esObsoletaCuandoCambiaLaComplejidadReportada() {
		LegalCase legalCase = legalCase();
		MlClassification classification = classificationWithSnapshot(legalCase, null);
		legalCase.setProcedureComplexity(CaseComplexity.ALTA);
		assertTrue(CaseWorkflowService.computeIsStale(legalCase, null, classification));
	}

	@Test
	void esObsoletaCuandoCambianLosBooleanosDeRiesgo() {
		LegalCase legalCase = legalCase();
		MlClassification classification = classificationWithSnapshot(legalCase, null);
		legalCase.setHasPriorComplaints(true);
		assertTrue(CaseWorkflowService.computeIsStale(legalCase, null, classification));
	}

	@Test
	void esObsoletaCuandoCambiaLaFechaDelEventoEnElContexto() {
		LegalCase legalCase = legalCase();
		MlClassification classification = classificationWithSnapshot(legalCase, "2026-01-10");

		CaseContext context = new CaseContext(legalCase, "Caso-0001", "Psiquiatría");
		context.setEventDate(LocalDate.parse("2026-02-15"));

		assertTrue(CaseWorkflowService.computeIsStale(legalCase, context, classification));
	}

	@Test
	void noEsObsoletaCuandoLaFechaDelEventoCoincideConLaFotografia() {
		LegalCase legalCase = legalCase();
		MlClassification classification = classificationWithSnapshot(legalCase, "2026-01-10");

		CaseContext context = new CaseContext(legalCase, "Caso-0001", "Psiquiatría");
		context.setEventDate(LocalDate.parse("2026-01-10"));

		assertFalse(CaseWorkflowService.computeIsStale(legalCase, context, classification));
	}
}
