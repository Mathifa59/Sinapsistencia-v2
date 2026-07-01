package pe.sinapsistencia.cases.web.dto;

/** Body de POST /api/legal-cases/{id}/responses (HU-21). */
public record CreateLegalResponseRequest(
		String content,
		String recommendations,
		String observations) {
}
