package pe.sinapsistencia.cases.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.sinapsistencia.audit.infrastructure.Auditable;
import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.cases.application.CaseWorkflowService;
import pe.sinapsistencia.cases.application.LegalCaseService;
import pe.sinapsistencia.cases.web.dto.CaseDetailResponse;
import pe.sinapsistencia.cases.web.dto.CaseEventDto;
import pe.sinapsistencia.cases.web.dto.CaseReportDto;
import pe.sinapsistencia.cases.web.dto.CaseResponse;
import pe.sinapsistencia.cases.web.dto.CloseCaseRequest;
import pe.sinapsistencia.cases.web.dto.CreateCaseEventRequest;
import pe.sinapsistencia.cases.web.dto.CreateCaseRequest;
import pe.sinapsistencia.cases.web.dto.CreateLegalResponseRequest;
import pe.sinapsistencia.cases.web.dto.EditCaseRequest;
import pe.sinapsistencia.cases.web.dto.LegalResponseDto;
import pe.sinapsistencia.cases.web.dto.UpdateCaseRequest;
import pe.sinapsistencia.shared.api.ApiResponse;
import pe.sinapsistencia.shared.api.ListResponse;

/** Mismos paths que el BFF legacy: /api/legal-cases (en UI, "consultas"). */
@RestController
@RequestMapping("/api/legal-cases")
public class LegalCaseController {

	private final LegalCaseService caseService;
	private final CaseWorkflowService workflowService;

	public LegalCaseController(LegalCaseService caseService, CaseWorkflowService workflowService) {
		this.caseService = caseService;
		this.workflowService = workflowService;
	}

	@GetMapping
	public ApiResponse<ListResponse<CaseResponse>> list(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String priority,
			@RequestParam(required = false) String doctorId,
			@RequestParam(required = false) String search,
			@RequestParam(required = false, defaultValue = "false") boolean assignedOnly,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(caseService.list(user, status, priority, doctorId, search, assignedOnly, page, pageSize));
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

	@GetMapping("/{id}/detail")
	public ApiResponse<CaseDetailResponse> getDetail(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id) {
		return ApiResponse.ok(workflowService.getDetail(user, id));
	}

	@GetMapping("/{id}/report")
	public ApiResponse<CaseReportDto> getReport(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id) {
		return ApiResponse.ok(workflowService.getReport(user, id));
	}

	@PutMapping("/{id}")
	@Auditable(action = "update", resource = "legal_case")
	public ApiResponse<CaseResponse> update(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id,
			@RequestBody UpdateCaseRequest request) {
		return ApiResponse.ok(caseService.update(user, id, request));
	}

	@PutMapping("/{id}/edit")
	@Auditable(action = "update", resource = "legal_case")
	public ApiResponse<CaseResponse> edit(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id,
			@RequestBody EditCaseRequest request) {
		return ApiResponse.ok(workflowService.edit(user, id, request));
	}

	@PostMapping("/{id}/start-review")
	@Auditable(action = "update", resource = "legal_case")
	public ApiResponse<CaseResponse> startReview(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id) {
		return ApiResponse.ok(workflowService.startReview(user, id));
	}

	@PostMapping("/{id}/close")
	@Auditable(action = "update", resource = "legal_case")
	public ApiResponse<CaseResponse> close(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id,
			@RequestBody CloseCaseRequest request) {
		return ApiResponse.ok(workflowService.close(user, id, request));
	}

	@PostMapping("/{id}/events")
	@Auditable(action = "create", resource = "case_event")
	public ApiResponse<CaseEventDto> addEvent(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id,
			@RequestBody CreateCaseEventRequest request) {
		return ApiResponse.ok(workflowService.addEvent(user, id, request));
	}

	@PostMapping("/{id}/responses")
	@Auditable(action = "create", resource = "legal_response")
	public ApiResponse<LegalResponseDto> addResponse(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id,
			@RequestBody CreateLegalResponseRequest request) {
		return ApiResponse.ok(workflowService.addResponse(user, id, request));
	}

	@PatchMapping("/{id}/responses/{responseId}/review")
	@Auditable(action = "update", resource = "legal_response")
	public ApiResponse<LegalResponseDto> reviewResponse(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id,
			@PathVariable UUID responseId) {
		return ApiResponse.ok(workflowService.markResponseReviewed(user, id, responseId));
	}
}
