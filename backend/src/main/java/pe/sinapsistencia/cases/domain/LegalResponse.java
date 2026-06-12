package pe.sinapsistencia.cases.domain;

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

/**
 * Orientación médico-legal preliminar del abogado (HU-21).
 * {@code isReviewed}: el médico la marcó como revisada (HU-22).
 */
@Entity
@Table(name = "legal_responses")
public class LegalResponse {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "case_id", nullable = false)
	private LegalCase legalCase;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lawyer_id", nullable = false)
	private Profile lawyer;

	@Column(nullable = false)
	private String content;

	@Column
	private String recommendations;

	@Column
	private String observations;

	@Column(name = "is_reviewed", nullable = false)
	private boolean isReviewed;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected LegalResponse() {
	}

	public LegalResponse(LegalCase legalCase, Profile lawyer, String content) {
		this.legalCase = legalCase;
		this.lawyer = lawyer;
		this.content = content;
	}

	public UUID getId() {
		return id;
	}

	public LegalCase getLegalCase() {
		return legalCase;
	}

	public Profile getLawyer() {
		return lawyer;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getRecommendations() {
		return recommendations;
	}

	public void setRecommendations(String recommendations) {
		this.recommendations = recommendations;
	}

	public String getObservations() {
		return observations;
	}

	public void setObservations(String observations) {
		this.observations = observations;
	}

	public boolean isReviewed() {
		return isReviewed;
	}

	public void setReviewed(boolean reviewed) {
		isReviewed = reviewed;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
