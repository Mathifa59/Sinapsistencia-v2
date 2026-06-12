package pe.sinapsistencia.cases.domain;

import java.time.Instant;
import java.time.LocalDate;
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

/** Evento clínico-legal simulado de la línea de tiempo de la consulta (HU-26/27). */
@Entity
@Table(name = "case_events")
public class CaseEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "case_id", nullable = false)
	private LegalCase legalCase;

	@Column(name = "event_date", nullable = false)
	private LocalDate eventDate;

	@Column(name = "event_type", nullable = false, length = 100)
	private String eventType;

	@Column(nullable = false)
	private String description = "";

	@Column(name = "is_simulated", nullable = false)
	private final boolean isSimulated = true;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by", nullable = false)
	private Profile createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected CaseEvent() {
	}

	public CaseEvent(LegalCase legalCase, LocalDate eventDate, String eventType, String description,
			Profile createdBy) {
		this.legalCase = legalCase;
		this.eventDate = eventDate;
		this.eventType = eventType;
		this.description = description;
		this.createdBy = createdBy;
	}

	public UUID getId() {
		return id;
	}

	public LegalCase getLegalCase() {
		return legalCase;
	}

	public LocalDate getEventDate() {
		return eventDate;
	}

	public void setEventDate(LocalDate eventDate) {
		this.eventDate = eventDate;
	}

	public String getEventType() {
		return eventType;
	}

	public void setEventType(String eventType) {
		this.eventType = eventType;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isSimulated() {
		return isSimulated;
	}

	public Profile getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
