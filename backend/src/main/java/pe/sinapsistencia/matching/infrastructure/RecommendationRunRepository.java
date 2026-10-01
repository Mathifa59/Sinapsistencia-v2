package pe.sinapsistencia.matching.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.matching.domain.RecommendationRun;
import pe.sinapsistencia.matching.domain.RecommendationRunStatus;

@Repository
public interface RecommendationRunRepository extends JpaRepository<RecommendationRun, UUID> {

	Optional<RecommendationRun> findByDoctor_IdAndIdempotencyKey(UUID doctorId, String idempotencyKey);

	// H-02: orden determinístico (desempate por id) para historial y "última ejecución".
	List<RecommendationRun> findByLegalCase_IdOrderByCreatedAtDescIdDesc(UUID caseId);

	Optional<RecommendationRun> findFirstByLegalCase_IdAndStatusOrderByCreatedAtDescIdDesc(
			UUID caseId, RecommendationRunStatus status);

	// H-02: resumen del dashboard (sin caso puntual) -- última ejecución del médico en cualquier caso suyo.
	Optional<RecommendationRun> findFirstByDoctor_IdAndStatusOrderByCreatedAtDescIdDesc(
			UUID doctorId, RecommendationRunStatus status);
}
