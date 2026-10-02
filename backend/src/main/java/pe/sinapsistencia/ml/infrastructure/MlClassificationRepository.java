package pe.sinapsistencia.ml.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.ml.domain.MlClassification;

@Repository
public interface MlClassificationRepository extends JpaRepository<MlClassification, UUID> {

	Optional<MlClassification> findFirstByLegalCase_IdOrderByCreatedAtDesc(UUID caseId);

	// H-05: desempate determinístico por id cuando dos clasificaciones comparten createdAt
	// (p. ej. una reclasificación inmediatamente después de la original).
	Optional<MlClassification> findFirstByLegalCase_IdOrderByCreatedAtDescIdDesc(UUID caseId);

	List<MlClassification> findByLegalCase_IdOrderByCreatedAtDescIdDesc(UUID caseId);
}
