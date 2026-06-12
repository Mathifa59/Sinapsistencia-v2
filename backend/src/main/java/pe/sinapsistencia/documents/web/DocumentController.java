package pe.sinapsistencia.documents.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.sinapsistencia.audit.infrastructure.Auditable;
import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.documents.application.DocumentService;
import pe.sinapsistencia.documents.web.dto.CreateDocumentRequest;
import pe.sinapsistencia.documents.web.dto.DocumentResponse;
import pe.sinapsistencia.documents.web.dto.UpdateDocumentRequest;
import pe.sinapsistencia.shared.api.ApiResponse;
import pe.sinapsistencia.shared.api.ListResponse;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

	private final DocumentService documentService;

	public DocumentController(DocumentService documentService) {
		this.documentService = documentService;
	}

	@GetMapping
	public ApiResponse<ListResponse<DocumentResponse>> list(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String authorId,
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(documentService.list(user, status, type, authorId, search, page, pageSize));
	}

	@PostMapping
	@Auditable(action = "create", resource = "document")
	public ResponseEntity<ApiResponse<DocumentResponse>> create(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestBody CreateDocumentRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(documentService.create(user, request)));
	}

	@GetMapping("/{id}")
	public ApiResponse<DocumentResponse> get(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id) {
		return ApiResponse.ok(documentService.get(user, id));
	}

	@PutMapping("/{id}")
	@Auditable(action = "update", resource = "document")
	public ApiResponse<DocumentResponse> update(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id,
			@RequestBody UpdateDocumentRequest request) {
		return ApiResponse.ok(documentService.update(user, id, request));
	}
}
