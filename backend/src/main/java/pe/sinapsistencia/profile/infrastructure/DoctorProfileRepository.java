package pe.sinapsistencia.profile.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.profile.domain.DoctorProfile;

@Repository
public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, UUID> {

	Optional<DoctorProfile> findByUserId(UUID userId);

	@EntityGraph(attributePaths = { "user" })
	List<DoctorProfile> findAllByOrderByCreatedAtDesc();

	@EntityGraph(attributePaths = { "user" })
	List<DoctorProfile> findBySpecialtyIn(Collection<String> specialties);

	@EntityGraph(attributePaths = { "user" })
	List<DoctorProfile> findByUserIdIn(Collection<UUID> userIds);
}
