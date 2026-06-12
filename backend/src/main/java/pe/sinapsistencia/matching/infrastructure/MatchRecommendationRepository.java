package pe.sinapsistencia.matching.infrastructure;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.matching.domain.MatchRecommendation;

@Repository
public interface MatchRecommendationRepository extends JpaRepository<MatchRecommendation, UUID> {
}
