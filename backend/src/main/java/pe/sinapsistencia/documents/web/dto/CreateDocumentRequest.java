package pe.sinapsistencia.documents.web.dto;

/**
 * Body de POST /api/documents. El autor se toma del token (ownership).
 * {@code caseId} opcional vincula el documento a una consulta.
 */
public record CreateDocumentRequest(String title, String type, String caseId, String initialContent) {
}
