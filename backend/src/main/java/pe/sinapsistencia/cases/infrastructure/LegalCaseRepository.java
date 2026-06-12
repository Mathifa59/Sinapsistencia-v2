package pe.sinapsistencia.cases.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.cases.domain.LegalCase;

@Repository
public interface LegalCaseRepository extends JpaRepository<LegalCase, UUID>, JpaSpecificationExecutor<LegalCase> {

	/** Carga doctor y lawyer en el mismo query (evita N+1 en los listados). */
	@Override
	@EntityGraph(attributePaths = { "doctor", "lawyer" })
	Page<LegalCase> findAll(Specification<LegalCase> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "doctor", "lawyer" })
	Optional<LegalCase> findWithPeopleById(UUID id);

	/**
	 * Consultas sin abogado de los doctores indicados, aún abiertas en el flujo
	 * (pendiente/clasificada), ordenadas por prioridad real (no alfabética) y
	 * fecha — para /api/matching/relevant-cases.
	 */
	@EntityGraph(attributePaths = { "doctor" })
	@Query("""
			SELECT c FROM LegalCase c
			WHERE c.lawyer IS NULL
			  AND c.doctor.id IN :doctorIds
			  AND c.status IN (pe.sinapsistencia.cases.domain.CaseStatus.PENDIENTE,
			                   pe.sinapsistencia.cases.domain.CaseStatus.CLASIFICADA)
			ORDER BY CASE c.priority
			           WHEN pe.sinapsistencia.cases.domain.CasePriority.CRITICA THEN 4
			           WHEN pe.sinapsistencia.cases.domain.CasePriority.ALTA THEN 3
			           WHEN pe.sinapsistencia.cases.domain.CasePriority.MEDIA THEN 2
			           ELSE 1
			         END DESC,
			         c.createdAt DESC
			""")
	List<LegalCase> findRelevantUnassigned(@Param("doctorIds") Collection<UUID> doctorIds, Pageable pageable);
}
