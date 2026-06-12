package pe.sinapsistencia.documents.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.documents.domain.DocumentVersion;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

	List<DocumentVersion> findByDocumentIdOrderByVersionAsc(UUID documentId);

	List<DocumentVersion> findByDocumentIdInOrderByVersionAsc(Collection<UUID> documentIds);
}
