package pe.sinapsistencia.cases.infrastructure;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.cases.domain.CaseEvent;

@Repository
public interface CaseEventRepository extends JpaRepository<CaseEvent, UUID> {
}
