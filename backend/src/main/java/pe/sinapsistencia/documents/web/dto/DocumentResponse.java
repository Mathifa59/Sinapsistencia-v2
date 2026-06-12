package pe.sinapsistencia.documents.web.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import pe.sinapsistencia.documents.domain.Document;
import pe.sinapsistencia.documents.domain.DocumentSignature;
import pe.sinapsistencia.documents.domain.DocumentVersion;

/** Shape del documento — espeja el legacy (authorName, versions[], signatures[]). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentResponse(
		String id,
		String title,
		String type,
		String status,
		String caseId,
		String authorId,
		String authorName,
		String currentVersionId,
		List<VersionDto> versions,
		List<SignatureDto> signatures,
		Instant createdAt,
		Instant updatedAt) {

	public record VersionDto(
			String id,
			int version,
			String content,
			String fileUrl,
			String createdById,
			Instant createdAt,
			String notes) {

		public static VersionDto from(DocumentVersion v) {
			return new VersionDto(
					v.getId().toString(),
					v.getVersion(),
					v.getContent(),
					v.getFileUrl(),
					v.getCreatedBy().getId().toString(),
					v.getCreatedAt(),
					v.getNotes());
		}
	}

	public record SignatureDto(
			String id,
			String signerId,
			String type,
			Instant signedAt,
			boolean isValid,
			String hash) {

		public static SignatureDto from(DocumentSignature s) {
			return new SignatureDto(
					s.getId().toString(),
					s.getSigner().getId().toString(),
					s.getType().getValue(),
					s.getSignedAt(),
					s.isValid(),
					s.getHash());
		}
	}

	public static DocumentResponse from(Document doc, List<DocumentVersion> versions,
			List<DocumentSignature> signatures) {
		return new DocumentResponse(
				doc.getId().toString(),
				doc.getTitle(),
				doc.getType().getValue(),
				doc.getStatus().getValue(),
				doc.getLegalCase() == null ? null : doc.getLegalCase().getId().toString(),
				doc.getAuthor().getId().toString(),
				doc.getAuthor().getName(),
				doc.getCurrentVersionId() == null ? null : doc.getCurrentVersionId().toString(),
				versions.stream().map(VersionDto::from).toList(),
				signatures.stream().map(SignatureDto::from).toList(),
				doc.getCreatedAt(),
				doc.getUpdatedAt());
	}
}
