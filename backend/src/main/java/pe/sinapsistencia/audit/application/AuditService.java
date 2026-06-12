package pe.sinapsistencia.audit.application;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.sinapsistencia.audit.domain.AuditLog;
import pe.sinapsistencia.audit.infrastructure.AuditLogRepository;
import pe.sinapsistencia.audit.web.dto.AuditLogResponse;
import pe.sinapsistencia.auth.domain.Profile;
import pe.sinapsistencia.auth.infrastructure.ProfileRepository;
import pe.sinapsistencia.shared.api.ListResponse;
import pe.sinapsistencia.shared.exception.BadRequestException;

/** Bitácora de auditoría (HU-42): consulta admin + registro desde otros módulos. */
@Service
public class AuditService {

	private static final Logger log = LoggerFactory.getLogger(AuditService.class);

	private final AuditLogRepository auditRepository;
	private final ProfileRepository profileRepository;
	private final ObjectMapper objectMapper;

	public AuditService(AuditLogRepository auditRepository, ProfileRepository profileRepository,
			ObjectMapper objectMapper) {
		this.auditRepository = auditRepository;
		this.profileRepository = profileRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public ListResponse<AuditLogResponse> list(String action, String userId, String resource,
			String search, int page, int pageSize) {

		Specification<AuditLog> spec = (root, q, cb) -> cb.conjunction();

		if (action != null) {
			spec = spec.and((root, q, cb) -> cb.equal(root.get("action"), action));
		}
		if (userId != null) {
			UUID userUuid = parseUuid(userId);
			spec = spec.and((root, q, cb) -> cb.equal(root.get("user").get("id"), userUuid));
		}
		if (resource != null) {
			spec = spec.and((root, q, cb) -> cb.equal(root.get("resource"), resource));
		}
		if (search != null && !search.isBlank()) {
			String pattern = "%" + search.toLowerCase() + "%";
			spec = spec.and((root, q, cb) -> cb.or(
					cb.like(cb.lower(root.get("resource")), pattern),
					cb.like(cb.lower(root.get("action")), pattern)));
		}

		Page<AuditLog> result = auditRepository.findAll(spec,
				PageRequest.of(Math.max(page - 1, 0), pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));

		List<AuditLogResponse> items = result.getContent().stream()
				.map(l -> AuditLogResponse.from(l, parseDetails(l.getDetails())))
				.toList();

		// El legacy devuelve {data, total} sin campos de página en audit
		return ListResponse.simple(items, result.getTotalElements());
	}

	/**
	 * Registra una acción en la bitácora. REQUIRES_NEW + swallow: un fallo de
	 * auditoría nunca debe tumbar la operación de negocio.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(UUID userId, String action, String resource, String resourceId,
			String description, String ipAddress, String userAgent) {
		try {
			Profile user = userId == null ? null : profileRepository.findById(userId).orElse(null);
			AuditLog entry = new AuditLog(user, action, resource);
			entry.setResourceId(resourceId);
			if (description != null) {
				entry.setDetails(objectMapper.createObjectNode()
						.put("description", description).toString());
			}
			entry.setIpAddress(ipAddress);
			entry.setUserAgent(userAgent);
			auditRepository.save(entry);
		} catch (Exception ex) {
			log.warn("No se pudo registrar auditoría ({} {}): {}", action, resource, ex.getMessage());
		}
	}

	private JsonNode parseDetails(String details) {
		try {
			return details == null ? null : objectMapper.readTree(details);
		} catch (Exception ex) {
			return null;
		}
	}

	private static UUID parseUuid(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException("Identificador inválido: " + value);
		}
	}
}
