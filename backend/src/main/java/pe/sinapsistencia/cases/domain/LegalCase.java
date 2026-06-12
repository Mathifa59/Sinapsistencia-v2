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
 * La consulta médico-legal (HU-11). Tabla `cases` por fidelidad con los paths
 * del API legacy (/api/legal-cases); en la UI siempre se llama "consulta".
 * Flujo de estados HU-16; prioridad con justificación HU-30.
 */
@Entity
@Table(name = "cases")
public class LegalCase {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 255)
	private String title;

	@Column(nullable = false)
	private String description;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "doctor_id", nullable = false)
	private Profile doctor;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "lawyer_id")
	private Profile lawyer;

	@Column(nullable = false, length = 20)
	private CaseStatus status = CaseStatus.PENDIENTE;

	@Column(nullable = false, length = 20)
	private CasePriority priority = CasePriority.MEDIA;

	@Column(name = "priority_justification")
	private String priorityJustification;

	@Column(name = "medical_specialty", length = 150)
	private String medicalSpecialty;

	@Column(name = "event_type", length = 100)
	private String eventType;

	@Column(name = "perceived_urgency", length = 20)
	private CasePriority perceivedUrgency;

	@Column
	private String notes;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected LegalCase() {
	}

	public LegalCase(String title, String description, Profile doctor) {
		this.title = title;
		this.description = description;
		this.doctor = doctor;
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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Profile getDoctor() {
		return doctor;
	}

	public Profile getLawyer() {
		return lawyer;
	}

	public void setLawyer(Profile lawyer) {
		this.lawyer = lawyer;
	}

	public CaseStatus getStatus() {
		return status;
	}

	public void setStatus(CaseStatus status) {
		this.status = status;
	}

	public CasePriority getPriority() {
		return priority;
	}

	public void setPriority(CasePriority priority) {
		this.priority = priority;
	}

	public String getPriorityJustification() {
		return priorityJustification;
	}

	public void setPriorityJustification(String priorityJustification) {
		this.priorityJustification = priorityJustification;
	}

	public String getMedicalSpecialty() {
		return medicalSpecialty;
	}

	public void setMedicalSpecialty(String medicalSpecialty) {
		this.medicalSpecialty = medicalSpecialty;
	}

	public String getEventType() {
		return eventType;
	}

	public void setEventType(String eventType) {
		this.eventType = eventType;
	}

	public CasePriority getPerceivedUrgency() {
		return perceivedUrgency;
	}

	public void setPerceivedUrgency(CasePriority perceivedUrgency) {
		this.perceivedUrgency = perceivedUrgency;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
