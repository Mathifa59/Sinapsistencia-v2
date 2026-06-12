package pe.sinapsistencia.audit.infrastructure;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.audit.domain.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

	@Override
	@EntityGraph(attributePaths = { "user" })
	Page<AuditLog> findAll(Specification<AuditLog> spec, Pageable pageable);
}
