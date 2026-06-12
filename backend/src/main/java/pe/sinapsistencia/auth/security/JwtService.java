package pe.sinapsistencia.auth.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import pe.sinapsistencia.auth.domain.Profile;

/**
 * Emisión y validación de JWT (jjwt 0.12).
 * Claims: sub = userId, role (valor en español: doctor/lawyer/admin), email, name.
 */
@Service
public class JwtService {

	private final SecretKey key;
	private final long expirationMs;

	public JwtService(
			@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.expiration}") long expirationMs) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationMs = expirationMs;
	}

	public String generateToken(Profile profile) {
		Date now = new Date();
		return Jwts.builder()
				.subject(profile.getId().toString())
				.claim("role", profile.getRole().getValue())
				.claim("email", profile.getEmail())
				.claim("name", profile.getName())
				.issuedAt(now)
				.expiration(new Date(now.getTime() + expirationMs))
				.signWith(key)
				.compact();
	}

	/** Valida firma y expiración; lanza {@link io.jsonwebtoken.JwtException} si el token no es válido. */
	public Claims parseToken(String token) {
		return Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	public long getExpirationMs() {
		return expirationMs;
	}
}
