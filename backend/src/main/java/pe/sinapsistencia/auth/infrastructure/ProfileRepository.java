package pe.sinapsistencia.auth.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.auth.domain.Profile;
import pe.sinapsistencia.auth.domain.UserRole;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {

	Optional<Profile> findByEmail(String email);

	boolean existsByEmail(String email);

	List<Profile> findAllByOrderByCreatedAtDesc();

	List<Profile> findByRoleOrderByCreatedAtDesc(UserRole role);
}
