package pe.sinapsistencia.audit.domain;

import java.time.Instant;
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

/** Bitácora de auditoría (HU-10/42): acción, recurso, detalles, IP y user-agent. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private Profile user;

	@Column(nullable = false, length = 50)
	private String action;

	@Column(nullable = false, length = 100)
	private String resource;

	@Column(name = "resource_id", length = 100)
	private String resourceId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String details = "{}";

	@Column(name = "ip_address", length = 60)
	private String ipAddress;

	@Column(name = "user_agent")
	private String userAgent;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AuditLog() {
	}

	public AuditLog(Profile user, String action, String resource) {
		this.user = user;
		this.action = action;
		this.resource = resource;
	}

	public UUID getId() {
		return id;
	}

	public Profile getUser() {
		return user;
	}

	public String getAction() {
		return action;
	}

	public String getResource() {
		return resource;
	}

	public String getResourceId() {
		return resourceId;
	}

	public void setResourceId(String resourceId) {
		this.resourceId = resourceId;
	}

	public String getDetails() {
		return details;
	}

	public void setDetails(String details) {
		this.details = details;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
