package pe.sinapsistencia.cases.web.dto;

/** Body de PUT /api/legal-cases/{id} — igual que el legacy: {status?, lawyerId?} + notes (HU-38). */
public record UpdateCaseRequest(String status, String lawyerId, String notes) {
}
