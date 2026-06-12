package pe.sinapsistencia.matching.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.sinapsistencia.audit.infrastructure.Auditable;
import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.matching.application.MatchingService;
import pe.sinapsistencia.matching.web.dto.ContactRequestResponse;
import pe.sinapsistencia.matching.web.dto.DoctorCardDto;
import pe.sinapsistencia.matching.web.dto.LawyerCardDto;
import pe.sinapsistencia.matching.web.dto.RecommendationDto.RecommendationsResponse;
import pe.sinapsistencia.shared.api.ApiResponse;

/** Mismos paths que el BFF legacy: /api/matching/{doctors,lawyers,contact-requests,relevant-cases}. */
@RestController
@RequestMapping("/api/matching")
public class MatchingController {

	public record CreateContactRequestBody(String fromDoctorId, String toLawyerId, String message, String caseId) {
	}

	public record RespondContactRequestBody(String requestId, String status, String responseMessage) {
	}

	public record GenerateRecommendationsBody(String caseId) {
	}

	private final MatchingService matchingService;

	public MatchingController(MatchingService matchingService) {
		this.matchingService = matchingService;
	}

	@GetMapping("/doctors")
	public ApiResponse<List<DoctorCardDto>> doctors() {
		return ApiResponse.ok(matchingService.listDoctors());
	}

	/** Sin doctorId → directorio de abogados; con doctorId → recomendaciones ML (legacy). */
	@GetMapping("/lawyers")
	public ApiResponse<?> lawyers(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) String doctorId) {
		if (doctorId != null && !doctorId.isBlank()) {
			return ApiResponse.ok(matchingService.recommendations(user, doctorId));
		}
		return ApiResponse.ok(matchingService.listLawyers());
	}

	/** Genera recomendaciones (opcionalmente para una consulta) y las persiste con factores XAI (HU-31/32). */
	@PostMapping("/lawyers")
	public ResponseEntity<ApiResponse<RecommendationsResponse>> generateRecommendations(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestBody(required = false) GenerateRecommendationsBody body) {
		String caseId = body == null ? null : body.caseId();
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(matchingService.generateAndPersist(user, caseId)));
	}

	@GetMapping("/contact-requests")
	public ApiResponse<List<ContactRequestResponse>> contactRequests(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) String lawyerId,
			@RequestParam(required = false) String doctorId,
			@RequestParam(required = false) String status) {
		return ApiResponse.ok(matchingService.listContactRequests(user, lawyerId, doctorId, status));
	}

	@PostMapping("/contact-requests")
	@Auditable(action = "create", resource = "contact_request")
	public ResponseEntity<ApiResponse<ContactRequestResponse>> createContactRequest(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestBody CreateContactRequestBody body) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(matchingService.createContactRequest(
						user, body.toLawyerId(), body.message(), body.caseId())));
	}

	@PatchMapping("/contact-requests")
	@Auditable(action = "update", resource = "contact_request")
	public ApiResponse<ContactRequestResponse> respondContactRequest(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestBody RespondContactRequestBody body) {
		return ApiResponse.ok(matchingService.respondContactRequest(
				user, body.requestId(), body.status(), body.responseMessage()));
	}

	@GetMapping("/relevant-cases")
	public ApiResponse<Map<String, Object>> relevantCases(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) String lawyerId) {
		return ApiResponse.ok(matchingService.relevantCases(user, lawyerId));
	}
}
