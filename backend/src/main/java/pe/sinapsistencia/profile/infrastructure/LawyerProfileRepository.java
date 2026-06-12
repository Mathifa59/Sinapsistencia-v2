package pe.sinapsistencia.profile.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.profile.domain.LawyerProfile;

@Repository
public interface LawyerProfileRepository extends JpaRepository<LawyerProfile, UUID> {

	Optional<LawyerProfile> findByUserId(UUID userId);

	@EntityGraph(attributePaths = { "user" })
	List<LawyerProfile> findAllByOrderByRatingDesc();

	@EntityGraph(attributePaths = { "user" })
	List<LawyerProfile> findByUserIdIn(Collection<UUID> userIds);
}
