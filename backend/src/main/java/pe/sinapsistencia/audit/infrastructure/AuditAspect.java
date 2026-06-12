package pe.sinapsistencia.audit.infrastructure;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import pe.sinapsistencia.audit.application.AuditService;
import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.shared.api.ApiResponse;

/**
 * Bitácora de auditoría (HU-10/42): registra en audit_logs cada operación
 * marcada con {@link Auditable} que termina en una respuesta exitosa
 * ({@code ApiResponse.success() == true}).
 */
@Aspect
@Component
public class AuditAspect {

	private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

	private static final Map<String, String> RESOURCE_LABELS = Map.of(
			"auth", "sesión",
			"legal_case", "consulta",
			"document", "documento",
			"user", "usuario",
			"contact_request", "solicitud de contacto",
			"profile", "perfil");

	private final AuditService auditService;

	public AuditAspect(AuditService auditService) {
		this.auditService = auditService;
	}

	@AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
	public void audit(JoinPoint joinPoint, Auditable auditable, Object result) {
		try {
			ApiResponse<?> response = unwrap(result);
			if (response == null || !response.success()) {
				return;
			}

			Object data = response.data();
			UUID userId = resolveUserId(auditable, data);
			String action = resolveAction(auditable, data);
			String resourceId = resolveResourceId(data, userId);
			String description = buildDescription(action, auditable.resource(), data);

			HttpServletRequest request = currentRequest();
			String ipAddress = request == null ? null : resolveIp(request);
			String userAgent = request == null ? null : request.getHeader("User-Agent");

			auditService.record(userId, action, auditable.resource(), resourceId, description, ipAddress, userAgent);
		} catch (Exception ex) {
			log.warn("No se pudo registrar auditoría para {}: {}", joinPoint.getSignature(), ex.getMessage());
		}
	}

	private ApiResponse<?> unwrap(Object result) {
		Object body = result instanceof ResponseEntity<?> entity ? entity.getBody() : result;
		return body instanceof ApiResponse<?> apiResponse ? apiResponse : null;
	}

	private UUID resolveUserId(Auditable auditable, Object data) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser principal) {
			return principal.id();
		}
		if ("login".equals(auditable.action())) {
			Object user = invoke(data, "user");
			Object id = invoke(user, "id");
			if (id instanceof String idStr) {
				return UUID.fromString(idStr);
			}
		}
		return null;
	}

	/** HU-34: una actualización de documento que resulta en estado "firmado" se audita como firma. */
	private String resolveAction(Auditable auditable, Object data) {
		if ("document".equals(auditable.resource()) && "update".equals(auditable.action())
				&& "firmado".equals(invoke(data, "status"))) {
			return "sign";
		}
		return auditable.action();
	}

	private String resolveResourceId(Object data, UUID userId) {
		Object id = invoke(data, "id");
		if (id instanceof String idStr) {
			return idStr;
		}
		return userId == null ? null : userId.toString();
	}

	private String buildDescription(String action, String resource, Object data) {
		if ("auth".equals(resource)) {
			return "login".equals(action) ? "Inicio de sesión" : "Cierre de sesión";
		}

		String verb = switch (action) {
			case "create" -> "Creó";
			case "update" -> "Actualizó";
			case "sign" -> "Firmó";
			case "delete" -> "Eliminó";
			default -> action;
		};
		String label = RESOURCE_LABELS.getOrDefault(resource, resource);

		String title = firstNonBlank(invoke(data, "title"), invoke(data, "caseTitle"), invoke(data, "name"),
				invoke(data, "email"));
		return title != null ? verb + " " + label + " \"" + title + "\"" : verb + " " + label;
	}

	private String firstNonBlank(Object... values) {
		for (Object value : values) {
			if (value instanceof String str && !str.isBlank()) {
				return str;
			}
		}
		return null;
	}

	private Object invoke(Object target, String methodName) {
		if (target == null) {
			return null;
		}
		try {
			Method method = target.getClass().getMethod(methodName);
			return method.invoke(target);
		} catch (Exception ex) {
			return null;
		}
	}

	private HttpServletRequest currentRequest() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
			return attrs.getRequest();
		}
		return null;
	}

	private String resolveIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
