package pe.sinapsistencia.cases.web.dto;

/** Body de POST /api/legal-cases/{id}/close (HU-23). */
public record CloseCaseRequest(String reason) {
}
