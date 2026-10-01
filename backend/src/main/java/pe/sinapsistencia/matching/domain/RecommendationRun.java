package pe.sinapsistencia.matching.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
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
 * H-02: una ejecución de matching persistida — fotografía inmutable una vez
 * {@code completed} (inputs, corpus, pesos, resultado). Reemplaza el
 * recálculo silencioso de {@code RecommendationService.computeRecommendations}
 * en cada GET: el médico ve exactamente lo que generó, reproducible.
 */
@Entity
@Table(name = "recommendation_runs")
public class RecommendationRun {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "doctor_id", nullable = false)
	private Profile doctor;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "case_id", nullable = false)
	private LegalCase legalCase;

	@Column(name = "idempotency_key", nullable = false, length = 100)
	private String idempotencyKey;

	/** Huella del COMANDO del cliente (caseId + parámetros explícitos) -- compara reintentos de la misma clave. */
	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Column(nullable = false, length = 20)
	private RecommendationRunStatus status;

	/** 'ml' | 'fallback'. NULL mientras status != completed. */
	@Column(length = 20)
	private String origin;

	@Column(name = "model_version", length = 100)
	private String modelVersion;

	@Column(name = "pipeline_version", nullable = false, length = 100)
	private String pipelineVersion;

	@Column(name = "top_k", nullable = false)
	private int topK = 10;

	/** Huella de las entradas efectivamente usadas (perfil + caso) -- detecta antigüedad, no invalida el run. */
	@Column(name = "input_hash", length = 64)
	private String inputHash;

	@Column(name = "corpus_hash", length = 64)
	private String corpusHash;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "input_snapshot")
	private JsonNode inputSnapshot;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "corpus_snapshot")
	private JsonNode corpusSnapshot;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column
	private JsonNode weights;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "model_info")
	private JsonNode modelInfo;

	@Column(name = "candidate_count")
	private Integer candidateCount;

	@Column(name = "result_count")
	private Integer resultCount;

	@Column(name = "error_code", length = 100)
	private String errorCode;

	@Column(name = "error_message", columnDefinition = "text")
	private String errorMessage;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected RecommendationRun() {
	}

	public RecommendationRun(Profile doctor, LegalCase legalCase, String idempotencyKey, String requestHash,
			int topK, String pipelineVersion) {
		this.doctor = doctor;
		this.legalCase = legalCase;
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.topK = topK;
		this.pipelineVersion = pipelineVersion;
		this.status = RecommendationRunStatus.PROCESSING;
	}

	public UUID getId() {
		return id;
	}

	public Profile getDoctor() {
		return doctor;
	}

	public LegalCase getLegalCase() {
		return legalCase;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public RecommendationRunStatus getStatus() {
		return status;
	}

	public void setStatus(RecommendationRunStatus status) {
		this.status = status;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public String getModelVersion() {
		return modelVersion;
	}

	public void setModelVersion(String modelVersion) {
		this.modelVersion = modelVersion;
	}

	public String getPipelineVersion() {
		return pipelineVersion;
	}

	public int getTopK() {
		return topK;
	}

	public String getInputHash() {
		return inputHash;
	}

	public void setInputHash(String inputHash) {
		this.inputHash = inputHash;
	}

	public String getCorpusHash() {
		return corpusHash;
	}

	public void setCorpusHash(String corpusHash) {
		this.corpusHash = corpusHash;
	}

	public JsonNode getInputSnapshot() {
		return inputSnapshot;
	}

	public void setInputSnapshot(JsonNode inputSnapshot) {
		this.inputSnapshot = inputSnapshot;
	}

	public JsonNode getCorpusSnapshot() {
		return corpusSnapshot;
	}

	public void setCorpusSnapshot(JsonNode corpusSnapshot) {
		this.corpusSnapshot = corpusSnapshot;
	}

	public JsonNode getWeights() {
		return weights;
	}

	public void setWeights(JsonNode weights) {
		this.weights = weights;
	}

	public JsonNode getModelInfo() {
		return modelInfo;
	}

	public void setModelInfo(JsonNode modelInfo) {
		this.modelInfo = modelInfo;
	}

	public Integer getCandidateCount() {
		return candidateCount;
	}

	public void setCandidateCount(Integer candidateCount) {
		this.candidateCount = candidateCount;
	}

	public Integer getResultCount() {
		return resultCount;
	}

	public void setResultCount(Integer resultCount) {
		this.resultCount = resultCount;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}
}
