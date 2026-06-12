package pe.sinapsistencia.documents.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.sinapsistencia.auth.domain.Profile;
import pe.sinapsistencia.auth.infrastructure.ProfileRepository;
import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.cases.domain.LegalCase;
import pe.sinapsistencia.cases.infrastructure.LegalCaseRepository;
import pe.sinapsistencia.documents.domain.Document;
import pe.sinapsistencia.documents.domain.DocumentSignature;
import pe.sinapsistencia.documents.domain.DocumentStatus;
import pe.sinapsistencia.documents.domain.DocumentType;
import pe.sinapsistencia.documents.domain.DocumentVersion;
import pe.sinapsistencia.documents.domain.SignatureType;
import pe.sinapsistencia.documents.infrastructure.DocumentRepository;
import pe.sinapsistencia.documents.infrastructure.DocumentSignatureRepository;
import pe.sinapsistencia.documents.infrastructure.DocumentVersionRepository;
import pe.sinapsistencia.documents.web.dto.CreateDocumentRequest;
import pe.sinapsistencia.documents.web.dto.DocumentResponse;
import pe.sinapsistencia.documents.web.dto.UpdateDocumentRequest;
import pe.sinapsistencia.shared.api.ListResponse;
import pe.sinapsistencia.shared.exception.BadRequestException;
import pe.sinapsistencia.shared.exception.ForbiddenException;
import pe.sinapsistencia.shared.exception.NotFoundException;

/**
 * Documentos clínico-legales con trazabilidad (versiones + firmas).
 *
 * OWNERSHIP (HU-40): autor del documento, abogado asignado a la consulta
 * vinculada, o admin. La firma genera un hash SHA-256 REAL (deuda del legacy
 * cerrada aquí).
 */
@Service
public class DocumentService {

	private final DocumentRepository documentRepository;
	private final DocumentVersionRepository versionRepository;
	private final DocumentSignatureRepository signatureRepository;
	private final LegalCaseRepository caseRepository;
	private final ProfileRepository profileRepository;

	public DocumentService(DocumentRepository documentRepository,
			DocumentVersionRepository versionRepository,
			DocumentSignatureRepository signatureRepository,
			LegalCaseRepository caseRepository,
			ProfileRepository profileRepository) {
		this.documentRepository = documentRepository;
		this.versionRepository = versionRepository;
		this.signatureRepository = signatureRepository;
		this.caseRepository = caseRepository;
		this.profileRepository = profileRepository;
	}

	@Transactional(readOnly = true)
	public ListResponse<DocumentResponse> list(AuthenticatedUser user, String status, String type,
			String authorId, String search, int page, int pageSize) {

		Specification<Document> spec = ownershipSpec(user);

		if (status != null) {
			DocumentStatus statusEnum = parse(() -> DocumentStatus.fromValue(status));
			spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), statusEnum));
		}
		if (type != null) {
			DocumentType typeEnum = parse(() -> DocumentType.fromValue(type));
			spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), typeEnum));
		}
		// authorId del query param solo aplica para admin (para doctor manda el token)
		if (authorId != null && user.role() == pe.sinapsistencia.auth.domain.UserRole.ADMIN) {
			UUID authorUuid = UUID.fromString(authorId);
			spec = spec.and((root, q, cb) -> cb.equal(root.get("author").get("id"), authorUuid));
		}
		if (search != null && !search.isBlank()) {
			String pattern = "%" + search.toLowerCase() + "%";
			spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("title")), pattern));
		}

		Page<Document> result = documentRepository.findAll(spec,
				PageRequest.of(Math.max(page - 1, 0), pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));

		List<UUID> docIds = result.getContent().stream().map(Document::getId).toList();
		Map<UUID, List<DocumentVersion>> versions = docIds.isEmpty() ? Map.of()
				: versionRepository.findByDocumentIdInOrderByVersionAsc(docIds).stream()
						.collect(Collectors.groupingBy(v -> v.getDocument().getId()));
		Map<UUID, List<DocumentSignature>> signatures = docIds.isEmpty() ? Map.of()
				: signatureRepository.findByDocumentIdIn(docIds).stream()
						.collect(Collectors.groupingBy(s -> s.getDocument().getId()));

		List<DocumentResponse> items = result.getContent().stream()
				.map(d -> DocumentResponse.from(d,
						versions.getOrDefault(d.getId(), List.of()),
						signatures.getOrDefault(d.getId(), List.of())))
				.toList();

		return ListResponse.paged(items, result.getTotalElements(), page, pageSize);
	}

	@Transactional
	public DocumentResponse create(AuthenticatedUser user, CreateDocumentRequest request) {
		if (isBlank(request.title()) || isBlank(request.type())) {
			throw new BadRequestException("Título y tipo son requeridos");
		}

		Profile author = profileRepository.findById(user.id())
				.orElseThrow(() -> new NotFoundException("Perfil no encontrado"));

		Document document = new Document(request.title(),
				parse(() -> DocumentType.fromValue(request.type())), author);

		if (request.caseId() != null) {
			LegalCase legalCase = caseRepository.findWithPeopleById(UUID.fromString(request.caseId()))
					.orElseThrow(() -> new NotFoundException("Caso no encontrado"));
			// Solo se puede vincular a una consulta propia (o siendo admin)
			if (user.role() != pe.sinapsistencia.auth.domain.UserRole.ADMIN
					&& !legalCase.getDoctor().getId().equals(user.id())) {
				throw new ForbiddenException("No puedes vincular documentos a una consulta ajena");
			}
			document.setLegalCase(legalCase);
		}

		document = documentRepository.save(document);

		DocumentVersion version = new DocumentVersion(document, 1,
				request.initialContent() == null ? "" : request.initialContent(), author);
		version = versionRepository.save(version);

		document.setCurrentVersionId(version.getId());
		document = documentRepository.save(document);

		return DocumentResponse.from(document, List.of(version), List.of());
	}

	@Transactional(readOnly = true)
	public DocumentResponse get(AuthenticatedUser user, UUID id) {
		Document document = documentRepository.findWithRelationsById(id)
				.orElseThrow(() -> new NotFoundException("Documento no encontrado"));

		assertCanView(user, document);

		return DocumentResponse.from(document,
				versionRepository.findByDocumentIdOrderByVersionAsc(id),
				signatureRepository.findByDocumentId(id));
	}

	@Transactional
	public DocumentResponse update(AuthenticatedUser user, UUID id, UpdateDocumentRequest request) {
		if (isBlank(request.status())) {
			throw new BadRequestException("El campo status es requerido");
		}

		Document document = documentRepository.findWithRelationsById(id)
				.orElseThrow(() -> new NotFoundException("Documento no encontrado"));

		assertCanView(user, document);

		DocumentStatus newStatus = parse(() -> DocumentStatus.fromValue(request.status()));
		document.setStatus(newStatus);

		// Al marcar como FIRMADO se registra la firma con hash SHA-256 real
		// sobre el contenido de la versión vigente (HU-34; deuda legacy cerrada).
		if (newStatus == DocumentStatus.FIRMADO
				&& !signatureRepository.existsByDocumentIdAndSignerId(id, user.id())) {
			Profile signer = profileRepository.findById(user.id())
					.orElseThrow(() -> new NotFoundException("Perfil no encontrado"));
			String content = versionRepository.findByDocumentIdOrderByVersionAsc(id).stream()
					.reduce((first, second) -> second)
					.map(DocumentVersion::getContent)
					.orElse("");
			String hash = sha256(document.getId() + "|" + content + "|" + signer.getId() + "|" + Instant.now());
			signatureRepository.save(new DocumentSignature(document, signer, SignatureType.DIGITAL, hash));
		}

		document = documentRepository.save(document);

		return DocumentResponse.from(document,
				versionRepository.findByDocumentIdOrderByVersionAsc(id),
				signatureRepository.findByDocumentId(id));
	}

	// ── Ownership ───────────────────────────────────────────────────────────

	private Specification<Document> ownershipSpec(AuthenticatedUser user) {
		return switch (user.role()) {
			case DOCTOR -> (root, q, cb) -> cb.equal(root.get("author").get("id"), user.id());
			case LAWYER -> (root, q, cb) -> cb.equal(root.get("legalCase").get("lawyer").get("id"), user.id());
			case ADMIN -> (root, q, cb) -> cb.conjunction();
		};
	}

	private void assertCanView(AuthenticatedUser user, Document document) {
		boolean allowed = switch (user.role()) {
			case DOCTOR -> document.getAuthor().getId().equals(user.id());
			case LAWYER -> document.getLegalCase() != null
					&& document.getLegalCase().getLawyer() != null
					&& document.getLegalCase().getLawyer().getId().equals(user.id());
			case ADMIN -> true;
		};
		if (!allowed) {
			throw new ForbiddenException("No tienes permisos para acceder a este documento");
		}
	}

	// ── Helpers ─────────────────────────────────────────────────────────────

	private static String sha256(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 no disponible", ex);
		}
	}

	private static <T> T parse(java.util.function.Supplier<T> supplier) {
		try {
			return supplier.get();
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException(ex.getMessage());
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
