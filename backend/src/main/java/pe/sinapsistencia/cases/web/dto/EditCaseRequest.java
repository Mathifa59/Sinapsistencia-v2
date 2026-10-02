package pe.sinapsistencia.cases.web.dto;

/**
 * Body de PUT /api/legal-cases/{id}/edit — solo consultas en estado pendiente (HU-15).
 * H-05: todos los campos son opcionales por diseño -- omitir un campo significa
 * CONSERVAR su valor actual, nunca resetearlo. El formulario de edición se inicializa
 * siempre desde {@code CaseResponse}, no desde los defaults de creación.
 */
public record EditCaseRequest(
		String title,
		String description,
		String priority,
		String medicalSpecialty,
		String eventType,
		String perceivedUrgency,
		String procedureComplexity,
		Boolean documentationComplete,
		Boolean informedConsent,
		Boolean hasPriorComplaints,
		String notes,
		CreateCaseRequest.ContextPayload context) {
}
