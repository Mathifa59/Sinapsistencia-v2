package pe.sinapsistencia.auth.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.auth.domain.EmailVerificationToken;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

	Optional<EmailVerificationToken> findByTokenHashAndUsedAtIsNull(String tokenHash);

	/** Tokens aún vigentes del perfil: se invalidan al emitir uno nuevo. */
	List<EmailVerificationToken> findByProfileIdAndUsedAtIsNull(UUID profileId);

	/** Último token emitido: sirve para limitar la frecuencia de reenvíos. */
	Optional<EmailVerificationToken> findFirstByProfileIdOrderByCreatedAtDesc(UUID profileId);
}
