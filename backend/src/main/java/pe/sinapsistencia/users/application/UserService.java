package pe.sinapsistencia.users.application;

import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.sinapsistencia.auth.domain.Profile;
import pe.sinapsistencia.auth.domain.UserRole;
import pe.sinapsistencia.auth.infrastructure.ProfileRepository;
import pe.sinapsistencia.auth.web.dto.UserDto;
import pe.sinapsistencia.shared.exception.BadRequestException;
import pe.sinapsistencia.shared.exception.ConflictException;
import pe.sinapsistencia.shared.exception.NotFoundException;

/** Gestión de usuarios (HU-24/25/26). Acceso restringido a ADMIN en el controller. */
@Service
public class UserService {

	/** Password por defecto al crear usuario sin password — igual que el legacy. */
	private static final String DEFAULT_PASSWORD = "Sinapsistencia2024!";

	private final ProfileRepository profileRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(ProfileRepository profileRepository, PasswordEncoder passwordEncoder) {
		this.profileRepository = profileRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public List<UserDto> list(String role) {
		List<Profile> profiles = role == null
				? profileRepository.findAllByOrderByCreatedAtDesc()
				: profileRepository.findByRoleOrderByCreatedAtDesc(parseRole(role));
		return profiles.stream().map(UserDto::from).toList();
	}

	@Transactional
	public UserDto create(String name, String email, String role, String password) {
		if (isBlank(name) || isBlank(email) || isBlank(role)) {
			throw new BadRequestException("Nombre, email y rol son requeridos");
		}
		if (profileRepository.existsByEmail(email)) {
			throw new ConflictException("El email ya está registrado");
		}

		Profile profile = new Profile(email, name, parseRole(role),
				passwordEncoder.encode(isBlank(password) ? DEFAULT_PASSWORD : password));
		return UserDto.from(profileRepository.save(profile));
	}

	@Transactional(readOnly = true)
	public UserDto get(UUID id) {
		return UserDto.from(find(id));
	}

	/** PUT /api/users/{id}: actualiza nombre y/o rol. */
	@Transactional
	public UserDto update(UUID id, String name, String role) {
		if (isBlank(name) && isBlank(role)) {
			throw new BadRequestException("No se proporcionaron campos para actualizar");
		}
		Profile profile = find(id);
		if (!isBlank(name)) {
			profile.setName(name);
		}
		if (!isBlank(role)) {
			profile.setRole(parseRole(role));
		}
		return UserDto.from(profileRepository.save(profile));
	}

	/** PATCH /api/users/{id}: alterna activo/inactivo (igual que el legacy). */
	@Transactional
	public UserDto toggleActive(UUID id) {
		Profile profile = find(id);
		profile.setActive(!profile.isActive());
		return UserDto.from(profileRepository.save(profile));
	}

	private Profile find(UUID id) {
		return profileRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
	}

	private static UserRole parseRole(String role) {
		try {
			return UserRole.fromValue(role);
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException(ex.getMessage());
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
