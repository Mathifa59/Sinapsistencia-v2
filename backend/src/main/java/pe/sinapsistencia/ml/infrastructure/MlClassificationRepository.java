package pe.sinapsistencia.ml.infrastructure;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.ml.domain.MlClassification;

@Repository
public interface MlClassificationRepository extends JpaRepository<MlClassification, UUID> {
}
