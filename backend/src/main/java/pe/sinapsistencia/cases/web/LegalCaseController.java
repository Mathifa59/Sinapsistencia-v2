package pe.sinapsistencia.cases.web;

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
import pe.sinapsistencia.cases.application.LegalCaseService;
import pe.sinapsistencia.cases.web.dto.CaseResponse;
import pe.sinapsistencia.cases.web.dto.CreateCaseRequest;
import pe.sinapsistencia.cases.web.dto.UpdateCaseRequest;
import pe.sinapsistencia.shared.api.ApiResponse;
import pe.sinapsistencia.shared.api.ListResponse;

/** Mismos paths que el BFF legacy: /api/legal-cases (en UI, "consultas"). */
@RestController
@RequestMapping("/api/legal-cases")
public class LegalCaseController {

	private final LegalCaseService caseService;

	public LegalCaseController(LegalCaseService caseService) {
		this.caseService = caseService;
	}

	@GetMapping
	public ApiResponse<ListResponse<CaseResponse>> list(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String priority,
			@RequestParam(required = false) String doctorId,
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(caseService.list(user, status, priority, doctorId, search, page, pageSize));
	}

	@PostMapping
	@Auditable(action = "create", resource = "legal_case")
	public ResponseEntity<ApiResponse<CaseResponse>> create(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestBody CreateCaseRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(caseService.create(user, request)));
	}

	@GetMapping("/{id}")
	public ApiResponse<CaseResponse> get(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id) {
		return ApiResponse.ok(caseService.get(user, id));
	}

	@PutMapping("/{id}")
	@Auditable(action = "update", resource = "legal_case")
	public ApiResponse<CaseResponse> update(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id,
			@RequestBody UpdateCaseRequest request) {
		return ApiResponse.ok(caseService.update(user, id, request));
	}
}
