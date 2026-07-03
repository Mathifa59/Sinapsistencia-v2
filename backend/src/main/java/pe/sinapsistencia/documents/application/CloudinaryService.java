package pe.sinapsistencia.documents.application;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;

import pe.sinapsistencia.shared.exception.BadRequestException;
import pe.sinapsistencia.shared.exception.ServiceUnavailableException;

/**
 * Sube archivos de soporte documental a Cloudinary.
 * Si CLOUDINARY_URL no está configurado el bean Cloudinary no existe y
 * este servicio lanza 503 en lugar de explotar al arrancar.
 */
@Service
public class CloudinaryService {

	private static final long MAX_BYTES = 10 * 1024 * 1024; // 10 MB
	private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of(
			"application/pdf",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			"image/png",
			"image/jpeg");

	private final Optional<Cloudinary> cloudinary;

	public CloudinaryService(Optional<Cloudinary> cloudinary) {
		this.cloudinary = cloudinary;
	}

	/**
	 * Sube el archivo y devuelve la URL segura (HTTPS) del recurso.
	 * Lanza BadRequestException para tipo/tamaño inválido, ServiceUnavailableException
	 * si Cloudinary no está configurado.
	 */
	public String upload(MultipartFile file, UUID documentId) {
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("El archivo no puede estar vacío");
		}
		if (file.getSize() > MAX_BYTES) {
			throw new BadRequestException("El archivo supera el límite de 10 MB");
		}
		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
			throw new BadRequestException("Tipo de archivo no permitido. Se aceptan PDF, DOCX, PNG y JPG");
		}

		Cloudinary cl = cloudinary.orElseThrow(() -> new ServiceUnavailableException(
				"El servicio de almacenamiento de archivos no está configurado"));

		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> result = cl.uploader().upload(file.getBytes(), Map.of(
					"folder", "sinapsistencia/documents/" + documentId,
					"resource_type", "auto",
					"public_id", UUID.randomUUID().toString()));
			return (String) result.get("secure_url");
		} catch (IOException ex) {
			throw new ServiceUnavailableException("No se pudo subir el archivo. Intenta de nuevo.");
		}
	}
}
