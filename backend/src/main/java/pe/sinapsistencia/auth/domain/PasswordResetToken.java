package pe.sinapsistencia.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String email;

	@Column(nullable = false, unique = true, length = 64)
	private String token;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private boolean used;

	/** Perfil (con correo confirmado) que solicitó el token; null en tokens anteriores a V18. */
	@Column(name = "profile_id")
	private UUID profileId;

	protected PasswordResetToken() {
	}

	public PasswordResetToken(String email, String token, Instant expiresAt) {
		this.email = email;
		this.token = token;
		this.expiresAt = expiresAt;
	}

	public PasswordResetToken(UUID profileId, String email, String token, Instant expiresAt) {
		this(email, token, expiresAt);
		this.profileId = profileId;
	}

	public UUID getProfileId() {
		return profileId;
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getToken() {
		return token;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public boolean isUsed() {
		return used;
	}

	public void setUsed(boolean used) {
		this.used = used;
	}
}
