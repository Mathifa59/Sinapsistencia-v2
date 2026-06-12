package pe.sinapsistencia.ml.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.ml.application.MlProxyService;
import pe.sinapsistencia.ml.application.N8nNotifier;
import pe.sinapsistencia.shared.api.ApiResponse;

/**
 * Proxy ML — mismos paths que el legacy. POST /risk dispara n8n SOLO si
 * riskLevel ∈ {alto, critico} (fire-and-forget). GET /health es público
 * (igual que el indicador de disponibilidad del legacy).
 */
@RestController
@RequestMapping("/api/ml")
public class MlController {

	private final MlProxyService mlProxyService;
	private final N8nNotifier n8nNotifier;

	public MlController(MlProxyService mlProxyService, N8nNotifier n8nNotifier) {
		this.mlProxyService = mlProxyService;
		this.n8nNotifier = n8nNotifier;
	}

	@PostMapping("/risk")
	public ApiResponse<Map<String, Object>> risk(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestBody Map<String, Object> body) {

		Map<String, Object> result = mlProxyService.riskAssessment(body);

		String riskLevel = (String) result.get("riskLevel");
		if ("alto".equals(riskLevel) || "critico".equals(riskLevel)) {
			Map<String, Object> alert = new LinkedHashMap<>();
			alert.put("caseId", result.get("caseId"));
			alert.put("riskScore", result.get("riskScore"));
			alert.put("riskLevel", riskLevel);
			alert.put("riskFactors", result.get("riskFactors"));
			alert.put("recommendations", result.get("recommendations"));
			alert.put("specialty", body.getOrDefault("specialty", "No especificada"));
			alert.put("doctorName", user != null ? user.name() : "No identificado");
			alert.put("doctorEmail", user != null ? user.email() : "");
			alert.put("documentationComplete", body.getOrDefault("documentation_complete", false));
			alert.put("informedConsent", body.getOrDefault("informed_consent", false));
			alert.put("evaluatedAt", Instant.now().toString());
			n8nNotifier.triggerRiskAlert(alert);
		}

		return ApiResponse.ok(result);
	}

	@GetMapping("/health")
	public ApiResponse<Map<String, Object>> health() {
		return ApiResponse.ok(mlProxyService.health());
	}
}
