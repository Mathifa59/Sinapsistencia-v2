package pe.sinapsistencia.audit.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.sinapsistencia.audit.application.AuditService;
import pe.sinapsistencia.audit.web.dto.AuditLogResponse;
import pe.sinapsistencia.shared.api.ApiResponse;
import pe.sinapsistencia.shared.api.ListResponse;

/** Bitácora de auditoría — solo ADMIN. Mismos query params que el legacy. */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

	private final AuditService auditService;

	public AuditController(AuditService auditService) {
		this.auditService = auditService;
	}

	@GetMapping
	public ApiResponse<ListResponse<AuditLogResponse>> list(
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String userId,
			@RequestParam(required = false) String resource,
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "50") int pageSize) {
		return ApiResponse.ok(auditService.list(action, userId, resource, search, page, pageSize));
	}
}
