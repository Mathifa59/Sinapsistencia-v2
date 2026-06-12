package pe.sinapsistencia.documents.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import pe.sinapsistencia.auth.domain.Profile;
import pe.sinapsistencia.cases.domain.LegalCase;

/**
 * Documento clínico-legal con trazabilidad (versiones + firmas).
 * {@code currentVersionId} se mapea como UUID plano para evitar la asociación
 * circular documents ↔ document_versions.
 */
@Entity
@Table(name = "documents")
public class Document {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 255)
	private String title;

	@Column(nullable = false, length = 40)
	private DocumentType type;

	@Column(nullable = false, length = 20)
	private DocumentStatus status = DocumentStatus.BORRADOR;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false)
	private Profile author;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "case_id")
	private LegalCase legalCase;

	@Column(name = "current_version_id")
	private UUID currentVersionId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Document() {
	}

	public Document(String title, DocumentType type, Profile author) {
		this.title = title;
		this.type = type;
		this.author = author;
	}

	public UUID getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public DocumentType getType() {
		return type;
	}

	public void setType(DocumentType type) {
		this.type = type;
	}

	public DocumentStatus getStatus() {
		return status;
	}

	public void setStatus(DocumentStatus status) {
		this.status = status;
	}

	public Profile getAuthor() {
		return author;
	}

	public LegalCase getLegalCase() {
		return legalCase;
	}

	public void setLegalCase(LegalCase legalCase) {
		this.legalCase = legalCase;
	}

	public UUID getCurrentVersionId() {
		return currentVersionId;
	}

	public void setCurrentVersionId(UUID currentVersionId) {
		this.currentVersionId = currentVersionId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
