package pe.sinapsistencia.cases.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.cases.domain.CaseContext;

@Repository
public interface CaseContextRepository extends JpaRepository<CaseContext, UUID> {

	Optional<CaseContext> findByLegalCaseId(UUID caseId);

	List<CaseContext> findByLegalCaseIdIn(Collection<UUID> caseIds);
}
