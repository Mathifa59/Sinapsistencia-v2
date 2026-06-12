package pe.sinapsistencia.documents.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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

/** Firma de documento; {@code hash} se genera realmente al firmar (deuda cerrada en esta migración). */
@Entity
@Table(name = "document_signatures")
public class DocumentSignature {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "document_id", nullable = false)
	private Document document;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "signer_id", nullable = false)
	private Profile signer;

	@Column(nullable = false, length = 30)
	private SignatureType type;

	@Column(length = 128)
	private String hash;

	@Column(name = "is_valid", nullable = false)
	private boolean isValid = true;

	@CreationTimestamp
	@Column(name = "signed_at", nullable = false, updatable = false)
	private Instant signedAt;

	protected DocumentSignature() {
	}

	public DocumentSignature(Document document, Profile signer, SignatureType type, String hash) {
		this.document = document;
		this.signer = signer;
		this.type = type;
		this.hash = hash;
	}

	public UUID getId() {
		return id;
	}

	public Document getDocument() {
		return document;
	}

	public Profile getSigner() {
		return signer;
	}

	public SignatureType getType() {
		return type;
	}

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

	public boolean isValid() {
		return isValid;
	}

	public void setValid(boolean valid) {
		isValid = valid;
	}

	public Instant getSignedAt() {
		return signedAt;
	}
}
