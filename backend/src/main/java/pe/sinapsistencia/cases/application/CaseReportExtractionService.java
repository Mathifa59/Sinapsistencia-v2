package pe.sinapsistencia.cases.application;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import pe.sinapsistencia.auth.domain.UserRole;
import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.ml.application.MlProxyService;
import pe.sinapsistencia.shared.exception.BadRequestException;
import pe.sinapsistencia.shared.exception.ForbiddenException;

/**
 * Propone los campos de un caso a partir de la imagen de un informe (OCR local en el
 * servicio ML). No crea ningún caso ni guarda la imagen: devuelve una propuesta que el
 * médico revisa en el formulario. La persistencia sigue siendo POST /api/legal-cases.
 */
@Service
public class CaseReportExtractionService {

	/** Debe coincidir con MAX_IMAGE_BYTES del servicio ML. */
	static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
	static final Set<String> ALLOWED_CONTENT_TYPES =
			Set.of("image/png", "image/jpeg", "image/webp", "image/bmp");

	private final MlProxyService mlProxyService;

	public CaseReportExtractionService(MlProxyService mlProxyService) {
		this.mlProxyService = mlProxyService;
	}

	public Map<String, Object> extract(AuthenticatedUser user, MultipartFile file) {
		if (user.role() != UserRole.DOCTOR) {
			throw new ForbiddenException("Solo los médicos pueden registrar casos");
		}
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("Adjunta la imagen del informe");
		}
		if (file.getSize() > MAX_IMAGE_BYTES) {
			throw new BadRequestException("La imagen supera el máximo de 8 MB");
		}
		String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
		if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
			throw new BadRequestException("Formato no admitido: usa una imagen PNG, JPG, WEBP o BMP");
		}
		try {
			return mlProxyService.extractCaseReport(file.getBytes(), file.getOriginalFilename(), contentType);
		} catch (IOException ex) {
			throw new BadRequestException("No se pudo leer el archivo adjunto");
		}
	}
}
