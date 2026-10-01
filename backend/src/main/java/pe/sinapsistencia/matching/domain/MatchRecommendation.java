package pe.sinapsistencia.matching.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

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

	/** H-02: ejecución que generó esta fila. NULL = fila legacy anterior a H-02. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "run_id")
	private RecommendationRun run;

	/** Posición en el ranking de su ejecución (1 = mejor match). NULL en filas legacy. */
	@Column
	private Integer rank;

	@Column(name = "score_raw", precision = 7, scale = 6)
	private BigDecimal scoreRaw;

	@Column(name = "content_score_raw", precision = 7, scale = 6)
	private BigDecimal contentScoreRaw;

	@Column(name = "performance_score_raw", precision = 7, scale = 6)
	private BigDecimal performanceScoreRaw;

	/** Fotografía del LawyerCardDto mostrado -- un cambio posterior de perfil no la reescribe. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "lawyer_snapshot")
	private JsonNode lawyerSnapshot;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "matched_specialties", columnDefinition = "text[]")
	private List<String> matchedSpecialties;

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

	public RecommendationRun getRun() {
		return run;
	}

	public void setRun(RecommendationRun run) {
		this.run = run;
	}

	public Integer getRank() {
		return rank;
	}

	public void setRank(Integer rank) {
		this.rank = rank;
	}

	public BigDecimal getScoreRaw() {
		return scoreRaw;
	}

	public void setScoreRaw(BigDecimal scoreRaw) {
		this.scoreRaw = scoreRaw;
	}

	public BigDecimal getContentScoreRaw() {
		return contentScoreRaw;
	}

	public void setContentScoreRaw(BigDecimal contentScoreRaw) {
		this.contentScoreRaw = contentScoreRaw;
	}

	public BigDecimal getPerformanceScoreRaw() {
		return performanceScoreRaw;
	}

	public void setPerformanceScoreRaw(BigDecimal performanceScoreRaw) {
		this.performanceScoreRaw = performanceScoreRaw;
	}

	public JsonNode getLawyerSnapshot() {
		return lawyerSnapshot;
	}

	public void setLawyerSnapshot(JsonNode lawyerSnapshot) {
		this.lawyerSnapshot = lawyerSnapshot;
	}

	public List<String> getMatchedSpecialties() {
		return matchedSpecialties;
	}

	public void setMatchedSpecialties(List<String> matchedSpecialties) {
		this.matchedSpecialties = matchedSpecialties;
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
