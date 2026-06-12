package pe.sinapsistencia.matching.domain;

import java.math.BigDecimal;
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

/** Solicitud de contacto médico → abogado (HU-20), con respuesta del abogado (HU-18/39). */
@Entity
@Table(name = "contact_requests")
public class ContactRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "from_doctor_id", nullable = false)
	private Profile fromDoctor;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "to_lawyer_id", nullable = false)
	private Profile toLawyer;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "case_id")
	private LegalCase legalCase;

	@Column(name = "case_title", length = 255)
	private String caseTitle;

	@Column(nullable = false)
	private String message;

	@Column(nullable = false, length = 20)
	private ContactRequestStatus status = ContactRequestStatus.PENDIENTE;

	@Column(name = "ml_score", precision = 5, scale = 2)
	private BigDecimal mlScore;

	@Column(name = "response_message")
	private String responseMessage;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ContactRequest() {
	}

	public ContactRequest(Profile fromDoctor, Profile toLawyer, String message) {
		this.fromDoctor = fromDoctor;
		this.toLawyer = toLawyer;
		this.message = message;
	}

	public UUID getId() {
		return id;
	}

	public Profile getFromDoctor() {
		return fromDoctor;
	}

	public Profile getToLawyer() {
		return toLawyer;
	}

	public LegalCase getLegalCase() {
		return legalCase;
	}

	public void setLegalCase(LegalCase legalCase) {
		this.legalCase = legalCase;
	}

	public String getCaseTitle() {
		return caseTitle;
	}

	public void setCaseTitle(String caseTitle) {
		this.caseTitle = caseTitle;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public ContactRequestStatus getStatus() {
		return status;
	}

	public void setStatus(ContactRequestStatus status) {
		this.status = status;
	}

	public BigDecimal getMlScore() {
		return mlScore;
	}

	public void setMlScore(BigDecimal mlScore) {
		this.mlScore = mlScore;
	}

	public String getResponseMessage() {
		return responseMessage;
	}

	public void setResponseMessage(String responseMessage) {
		this.responseMessage = responseMessage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
