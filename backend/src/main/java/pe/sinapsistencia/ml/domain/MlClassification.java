package pe.sinapsistencia.ml.domain;

import java.math.BigDecimal;
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

import pe.sinapsistencia.cases.domain.CasePriority;
import pe.sinapsistencia.cases.domain.LegalCase;

/** Resultado de la clasificación ML de una consulta (HU-29): tipo, urgencia, complejidad y especialidad sugerida. */
@Entity
@Table(name = "ml_classifications")
public class MlClassification {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "case_id", nullable = false)
	private LegalCase legalCase;

	@Column(name = "case_type", length = 100)
	private String caseType;

	@Column(length = 20)
	private CasePriority urgency;

	@Column(length = 20)
	private CaseComplexity complexity;

	@Column(name = "suggested_specialty", length = 150)
	private String suggestedSpecialty;

	@Column(precision = 5, scale = 4)
	private BigDecimal confidence;

	@Column(name = "model_version", length = 50)
	private String modelVersion;

	@Column(name = "response_time_ms")
	private Integer responseTimeMs;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected MlClassification() {
	}

	public MlClassification(LegalCase legalCase) {
		this.legalCase = legalCase;
	}

	public UUID getId() {
		return id;
	}

	public LegalCase getLegalCase() {
		return legalCase;
	}

	public String getCaseType() {
		return caseType;
	}

	public void setCaseType(String caseType) {
		this.caseType = caseType;
	}

	public CasePriority getUrgency() {
		return urgency;
	}

	public void setUrgency(CasePriority urgency) {
		this.urgency = urgency;
	}

	public CaseComplexity getComplexity() {
		return complexity;
	}

	public void setComplexity(CaseComplexity complexity) {
		this.complexity = complexity;
	}

	public String getSuggestedSpecialty() {
		return suggestedSpecialty;
	}

	public void setSuggestedSpecialty(String suggestedSpecialty) {
		this.suggestedSpecialty = suggestedSpecialty;
	}

	public BigDecimal getConfidence() {
		return confidence;
	}

	public void setConfidence(BigDecimal confidence) {
		this.confidence = confidence;
	}

	public String getModelVersion() {
		return modelVersion;
	}

	public void setModelVersion(String modelVersion) {
		this.modelVersion = modelVersion;
	}

	public Integer getResponseTimeMs() {
		return responseTimeMs;
	}

	public void setResponseTimeMs(Integer responseTimeMs) {
		this.responseTimeMs = responseTimeMs;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
