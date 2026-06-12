package pe.sinapsistencia.documents.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.documents.domain.DocumentSignature;

@Repository
public interface DocumentSignatureRepository extends JpaRepository<DocumentSignature, UUID> {

	List<DocumentSignature> findByDocumentId(UUID documentId);

	List<DocumentSignature> findByDocumentIdIn(Collection<UUID> documentIds);

	boolean existsByDocumentIdAndSignerId(UUID documentId, UUID signerId);
}
