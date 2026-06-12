package pe.sinapsistencia.documents.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.documents.domain.Document;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

	@Override
	@EntityGraph(attributePaths = { "author", "legalCase" })
	Page<Document> findAll(Specification<Document> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "author", "legalCase", "legalCase.lawyer" })
	Optional<Document> findWithRelationsById(UUID id);
}
