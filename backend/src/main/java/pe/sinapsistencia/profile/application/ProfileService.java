package pe.sinapsistencia.profile.application;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import pe.sinapsistencia.auth.domain.Profile;
import pe.sinapsistencia.auth.domain.UserRole;
import pe.sinapsistencia.auth.infrastructure.ProfileRepository;
import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.profile.infrastructure.DoctorProfileRepository;
import pe.sinapsistencia.profile.infrastructure.LawyerProfileRepository;
import pe.sinapsistencia.profile.web.dto.ProfessionalDto;
import pe.sinapsistencia.profile.web.dto.ProfileResponse;
import pe.sinapsistencia.profile.web.dto.UpdateProfileRequest;
import pe.sinapsistencia.shared.exception.BadRequestException;
import pe.sinapsistencia.shared.exception.ForbiddenException;
import pe.sinapsistencia.shared.exception.NotFoundException;
import pe.sinapsistencia.shared.exception.ServiceUnavailableException;

/**
 * Perfil propio (base + profesional) y subida de avatar a Cloudinary.
 * OWNERSHIP: cada usuario solo ve/edita su perfil; admin puede cualquiera.
 */
@Service
public class ProfileService {

	private static final Set<String> VALID_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

	private final ProfileRepository profileRepository;
	private final DoctorProfileRepository doctorProfileRepository;
	private final LawyerProfileRepository lawyerProfileRepository;
	private final ObjectProvider<Cloudinary> cloudinaryProvider;

	public ProfileService(ProfileRepository profileRepository,
			DoctorProfileRepository doctorProfileRepository,
			LawyerProfileRepository lawyerProfileRepository,
			ObjectProvider<Cloudinary> cloudinaryProvider) {
		this.profileRepository = profileRepository;
		this.doctorProfileRepository = doctorProfileRepository;
		this.lawyerProfileRepository = lawyerProfileRepository;
		this.cloudinaryProvider = cloudinaryProvider;
	}

	@Transactional(readOnly = true)
	public ProfileResponse get(AuthenticatedUser user, String userIdParam) {
		UUID targetId = resolveTarget(user, userIdParam);
		Profile profile = profileRepository.findById(targetId)
				.orElseThrow(() -> new NotFoundException("Perfil no encontrado"));

		ProfessionalDto professional = switch (profile.getRole()) {
			case DOCTOR -> doctorProfileRepository.findByUserId(targetId)
					.map(ProfessionalDto::from).orElse(null);
			case LAWYER -> lawyerProfileRepository.findByUserId(targetId)
					.map(ProfessionalDto::from).orElse(null);
			case ADMIN -> null;
		};

		return ProfileResponse.from(profile, professional);
	}

	@Transactional
	public void update(AuthenticatedUser user, UpdateProfileRequest request) {
		UUID targetId = resolveTarget(user, request.userId());
		Profile profile = profileRepository.findById(targetId)
				.orElseThrow(() -> new NotFoundException("Perfil no encontrado"));

		if (request.name() != null && !request.name().isBlank()) {
			profile.setName(request.name());
			profileRepository.save(profile);
		}

		var pro = request.professional();
		if (pro != null && profile.getRole() == UserRole.DOCTOR) {
			var doctor = doctorProfileRepository.findByUserId(targetId)
					.orElseThrow(() -> new NotFoundException("Perfil médico no encontrado"));
			if (pro.cmp() != null) doctor.setCmp(pro.cmp());
			if (pro.specialty() != null) doctor.setSpecialty(pro.specialty());
			if (pro.hospital() != null) doctor.setHospital(pro.hospital());
			if (pro.phone() != null) doctor.setPhone(pro.phone());
			if (pro.bio() != null) doctor.setBio(pro.bio());
			if (pro.yearsExperience() != null) doctor.setYearsExperience(pro.yearsExperience());
			doctorProfileRepository.save(doctor);
		}

		if (pro != null && profile.getRole() == UserRole.LAWYER) {
			var lawyer = lawyerProfileRepository.findByUserId(targetId)
					.orElseThrow(() -> new NotFoundException("Perfil legal no encontrado"));
			if (pro.cab() != null) lawyer.setCab(pro.cab());
			if (pro.specialties() != null) lawyer.setSpecialties(pro.specialties());
			if (pro.medicalAreas() != null) lawyer.setMedicalAreas(pro.medicalAreas());
			if (pro.phone() != null) lawyer.setPhone(pro.phone());
			if (pro.bio() != null) lawyer.setBio(pro.bio());
			if (pro.yearsExperience() != null) lawyer.setYearsExperience(pro.yearsExperience());
			if (pro.available() != null) lawyer.setAvailable(pro.available());
			lawyerProfileRepository.save(lawyer);
		}
	}

	/** Sube el avatar a Cloudinary (mismas validaciones y mensajes del legacy) y devuelve la URL. */
	@Transactional
	public String uploadAvatar(AuthenticatedUser user, String userIdParam, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("file y userId son requeridos");
		}
		if (file.getContentType() == null || !VALID_IMAGE_TYPES.contains(file.getContentType())) {
			throw new BadRequestException("Solo se permiten imágenes JPEG, PNG o WebP");
		}
		if (file.getSize() > MAX_AVATAR_BYTES) {
			throw new BadRequestException("La imagen no debe superar 2MB");
		}

		UUID targetId = resolveTarget(user, userIdParam);
		Profile profile = profileRepository.findById(targetId)
				.orElseThrow(() -> new NotFoundException("Perfil no encontrado"));

		Cloudinary cloudinary = cloudinaryProvider.getIfAvailable();
		if (cloudinary == null) {
			throw new ServiceUnavailableException(
					"Storage no configurado: define CLOUDINARY_URL en el backend");
		}

		String avatarUrl;
		try {
			Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
					"folder", "avatars",
					"public_id", targetId.toString(),
					"overwrite", true,
					"resource_type", "image"));
			avatarUrl = (String) result.get("secure_url");
		} catch (IOException ex) {
			throw new ServiceUnavailableException("Error al subir imagen: " + ex.getMessage());
		}

		profile.setAvatarUrl(avatarUrl);
		profileRepository.save(profile);
		return avatarUrl;
	}

	/** Ownership: el userId del request solo puede diferir del token si eres admin. */
	private UUID resolveTarget(AuthenticatedUser user, String userIdParam) {
		if (userIdParam == null || userIdParam.isBlank()) {
			return user.id();
		}
		UUID target;
		try {
			target = UUID.fromString(userIdParam);
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException("Identificador inválido: " + userIdParam);
		}
		if (!target.equals(user.id()) && user.role() != UserRole.ADMIN) {
			throw new ForbiddenException("No tienes permisos para acceder a este perfil");
		}
		return target;
	}
}
