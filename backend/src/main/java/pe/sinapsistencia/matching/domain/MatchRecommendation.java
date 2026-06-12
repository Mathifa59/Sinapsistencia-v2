package pe.sinapsistencia.matching.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * Recomendación de matching médico-abogado (HU-31).
 * {@code factors} (JSONB) guarda la explicación XAI (HU-32): factores con peso
 * y descripción en lenguaje no determinista.
 */
@Entity
@Table(name = "match_recommendations")
public class MatchRecommendation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "doctor_id", nullable = false)
	private Profile doctor;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lawyer_id", nullable = false)
	private Profile lawyer;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "case_id")
	private LegalCase legalCase;

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal score;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private List<String> reasons = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String factors = "[]";

	@Column(name = "algorithm_version", nullable = false, length = 50)
	private String algorithmVersion = "v1";

	@Column(name = "is_accepted")
	private Boolean isAccepted;

	@Column(name = "feedback_at")
	private Instant feedbackAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected MatchRecommendation() {
	}

	public MatchRecommendation(Profile doctor, Profile lawyer, BigDecimal score) {
		this.doctor = doctor;
		this.lawyer = lawyer;
		this.score = score;
	}

	public UUID getId() {
		return id;
	}

	public Profile getDoctor() {
		return doctor;
	}

	public Profile getLawyer() {
		return lawyer;
	}

	public LegalCase getLegalCase() {
		return legalCase;
	}

	public void setLegalCase(LegalCase legalCase) {
		this.legalCase = legalCase;
	}

	public BigDecimal getScore() {
		return score;
	}

	public void setScore(BigDecimal score) {
		this.score = score;
	}

	public List<String> getReasons() {
		return reasons;
	}

	public void setReasons(List<String> reasons) {
		this.reasons = reasons;
	}

	public String getFactors() {
		return factors;
	}

	public void setFactors(String factors) {
		this.factors = factors;
	}

	public String getAlgorithmVersion() {
		return algorithmVersion;
	}

	public void setAlgorithmVersion(String algorithmVersion) {
		this.algorithmVersion = algorithmVersion;
	}

	public Boolean getIsAccepted() {
		return isAccepted;
	}

	public void setIsAccepted(Boolean accepted) {
		isAccepted = accepted;
	}

	public Instant getFeedbackAt() {
		return feedbackAt;
	}

	public void setFeedbackAt(Instant feedbackAt) {
		this.feedbackAt = feedbackAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
