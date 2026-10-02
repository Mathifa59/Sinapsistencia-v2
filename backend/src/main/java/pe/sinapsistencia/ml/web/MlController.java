package pe.sinapsistencia.ml.web;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.ml.application.MlProxyService;
import pe.sinapsistencia.ml.application.ModelMetricService;
import pe.sinapsistencia.ml.web.dto.ModelMetricDto;
import pe.sinapsistencia.shared.api.ApiResponse;

/**
 * Proxy ML — mismos paths que el legacy. GET /health es público (igual que el
 * indicador de disponibilidad del legacy).
 *
 * <p>H-06: POST /risk ya NO dispara la alerta de riesgo -- ese disparo vivía
 * aquí en paralelo al de {@code CaseClassificationService} (que SÍ persiste la
 * clasificación), duplicando el aviso sin un {@code classificationId} real
 * para el outbox nuevo. Este endpoint no tiene ningún consumidor en el
 * frontend (el flujo real de creación de casos clasifica server-side desde
 * {@code LegalCaseService}); queda como proxy puro sin el efecto secundario
 * que no correspondía a este path.
 */
@RestController
@RequestMapping("/api/ml")
public class MlController {

	private final MlProxyService mlProxyService;
	private final ModelMetricService modelMetricService;

	public MlController(MlProxyService mlProxyService, ModelMetricService modelMetricService) {
		this.mlProxyService = mlProxyService;
		this.modelMetricService = modelMetricService;
	}

	@PostMapping("/risk")
	public ApiResponse<Map<String, Object>> risk(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestBody Map<String, Object> body) {
		return ApiResponse.ok(mlProxyService.riskAssessment(body));
	}

	@GetMapping("/health")
	public ApiResponse<Map<String, Object>> health() {
		return ApiResponse.ok(mlProxyService.health());
	}

	@GetMapping("/metrics")
	@PreAuthorize("hasRole('ADMIN')")
	public ApiResponse<List<ModelMetricDto>> metrics() {
		return ApiResponse.ok(modelMetricService.list());
	}
}
