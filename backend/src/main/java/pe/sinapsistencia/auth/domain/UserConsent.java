package pe.sinapsistencia.auth.domain;

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

/**
 * Consentimiento registrado del titular de datos (Ley 29733): evidencia de la
 * aceptación de la política de privacidad al registrarse, con versión y fecha.
 */
@Entity
@Table(name = "user_consents")
public class UserConsent {

	public static final String TYPE_PRIVACY_POLICY = "privacy_policy";
	public static final String CURRENT_POLICY_VERSION = "v1";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private Profile user;

	@Column(name = "consent_type", nullable = false, length = 50)
	private String consentType;

	@Column(name = "policy_version", nullable = false, length = 20)
	private String policyVersion;

	@CreationTimestamp
	@Column(name = "accepted_at", nullable = false, updatable = false)
	private Instant acceptedAt;

	protected UserConsent() {
	}

	public UserConsent(Profile user, String consentType, String policyVersion) {
		this.user = user;
		this.consentType = consentType;
		this.policyVersion = policyVersion;
	}

	public UUID getId() {
		return id;
	}

	public Profile getUser() {
		return user;
	}

	public String getConsentType() {
		return consentType;
	}

	public String getPolicyVersion() {
		return policyVersion;
	}

	public Instant getAcceptedAt() {
		return acceptedAt;
	}
}
