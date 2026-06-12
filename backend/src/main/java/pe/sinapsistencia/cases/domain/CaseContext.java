package pe.sinapsistencia.cases.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Contexto clínico-legal SIMULADO de la consulta (HU-12, Ley 29733).
 * Sin datos identificables: el identificador es un alias ({@code contextCode},
 * ej. "Caso-001") y {@code isSimulated} es siempre true (CHECK en BD).
 */
@Entity
@Table(name = "case_context")
public class CaseContext {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "case_id", nullable = false, unique = true)
	private LegalCase legalCase;

	@Column(name = "context_code", nullable = false, length = 50)
	private String contextCode;

	@Column(name = "age_reference")
	private Integer ageReference;

	@Column(name = "medical_area", nullable = false, length = 150)
	private String medicalArea;

	@Column(name = "event_date")
	private LocalDate eventDate;

	@Column(nullable = false)
	private String summary = "";

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "relevant_factors", nullable = false, columnDefinition = "text[]")
	private List<String> relevantFactors = new ArrayList<>();

	@Column(name = "is_simulated", nullable = false)
	private final boolean isSimulated = true;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CaseContext() {
	}

	public CaseContext(LegalCase legalCase, String contextCode, String medicalArea) {
		this.legalCase = legalCase;
		this.contextCode = contextCode;
		this.medicalArea = medicalArea;
	}

	public UUID getId() {
		return id;
	}

	public LegalCase getLegalCase() {
		return legalCase;
	}

	public String getContextCode() {
		return contextCode;
	}

	public void setContextCode(String contextCode) {
		this.contextCode = contextCode;
	}

	public Integer getAgeReference() {
		return ageReference;
	}

	public void setAgeReference(Integer ageReference) {
		this.ageReference = ageReference;
	}

	public String getMedicalArea() {
		return medicalArea;
	}

	public void setMedicalArea(String medicalArea) {
		this.medicalArea = medicalArea;
	}

	public LocalDate getEventDate() {
		return eventDate;
	}

	public void setEventDate(LocalDate eventDate) {
		this.eventDate = eventDate;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public List<String> getRelevantFactors() {
		return relevantFactors;
	}

	public void setRelevantFactors(List<String> relevantFactors) {
		this.relevantFactors = relevantFactors;
	}

	public boolean isSimulated() {
		return isSimulated;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
