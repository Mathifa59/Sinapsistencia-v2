package pe.sinapsistencia.ml.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Métricas registradas del modelo ML (HU-35): precisión, recall, F1, % matching pertinente, tiempos. */
@Entity
@Table(name = "model_metrics")
public class ModelMetric {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "model_name", nullable = false, length = 100)
	private String modelName;

	@Column(name = "model_version", nullable = false, length = 50)
	private String modelVersion;

	@Column(name = "precision_score", precision = 5, scale = 4)
	private BigDecimal precisionScore;

	@Column(name = "recall_score", precision = 5, scale = 4)
	private BigDecimal recallScore;

	@Column(name = "f1_score", precision = 5, scale = 4)
	private BigDecimal f1Score;

	@Column(name = "matching_relevance_rate", precision = 5, scale = 4)
	private BigDecimal matchingRelevanceRate;

	@Column(name = "avg_response_time_ms")
	private Integer avgResponseTimeMs;

	@Column(name = "dataset_size")
	private Integer datasetSize;

	@Column
	private String notes;

	@CreationTimestamp
	@Column(name = "evaluated_at", nullable = false, updatable = false)
	private Instant evaluatedAt;

	protected ModelMetric() {
	}

	public ModelMetric(String modelName, String modelVersion) {
		this.modelName = modelName;
		this.modelVersion = modelVersion;
	}

	public UUID getId() {
		return id;
	}

	public String getModelName() {
		return modelName;
	}

	public String getModelVersion() {
		return modelVersion;
	}

	public BigDecimal getPrecisionScore() {
		return precisionScore;
	}

	public void setPrecisionScore(BigDecimal precisionScore) {
		this.precisionScore = precisionScore;
	}

	public BigDecimal getRecallScore() {
		return recallScore;
	}

	public void setRecallScore(BigDecimal recallScore) {
		this.recallScore = recallScore;
	}

	public BigDecimal getF1Score() {
		return f1Score;
	}

	public void setF1Score(BigDecimal f1Score) {
		this.f1Score = f1Score;
	}

	public BigDecimal getMatchingRelevanceRate() {
		return matchingRelevanceRate;
	}

	public void setMatchingRelevanceRate(BigDecimal matchingRelevanceRate) {
		this.matchingRelevanceRate = matchingRelevanceRate;
	}

	public Integer getAvgResponseTimeMs() {
		return avgResponseTimeMs;
	}

	public void setAvgResponseTimeMs(Integer avgResponseTimeMs) {
		this.avgResponseTimeMs = avgResponseTimeMs;
	}

	public Integer getDatasetSize() {
		return datasetSize;
	}

	public void setDatasetSize(Integer datasetSize) {
		this.datasetSize = datasetSize;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public Instant getEvaluatedAt() {
		return evaluatedAt;
	}
}
