package pe.sinapsistencia.documents.web.dto;

/** Body de PUT /api/documents/{id} — igual que el legacy: {status}. */
public record UpdateDocumentRequest(String status) {
}
