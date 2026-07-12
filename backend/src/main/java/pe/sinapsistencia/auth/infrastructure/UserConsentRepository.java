package pe.sinapsistencia.auth.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.sinapsistencia.auth.domain.UserConsent;

public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {

	List<UserConsent> findByUserId(UUID userId);
}
