package pe.sinapsistencia.auth.application;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.sinapsistencia.auth.domain.Profile;
import pe.sinapsistencia.auth.domain.UserRole;
import pe.sinapsistencia.auth.infrastructure.ProfileRepository;
import pe.sinapsistencia.auth.security.JwtService;
import pe.sinapsistencia.auth.web.dto.LoginResponse;
import pe.sinapsistencia.auth.web.dto.RegisterRequest;
import pe.sinapsistencia.auth.web.dto.UserDto;
import pe.sinapsistencia.profile.domain.DoctorProfile;
import pe.sinapsistencia.profile.domain.LawyerProfile;
import pe.sinapsistencia.profile.infrastructure.DoctorProfileRepository;
import pe.sinapsistencia.profile.infrastructure.LawyerProfileRepository;
import pe.sinapsistencia.shared.exception.BadRequestException;
import pe.sinapsistencia.shared.exception.ConflictException;
import pe.sinapsistencia.shared.exception.ForbiddenException;
import pe.sinapsistencia.shared.exception.NotFoundException;
import pe.sinapsistencia.shared.exception.ServiceUnavailableException;
import pe.sinapsistencia.shared.exception.UnauthorizedException;

/**
 * Casos de uso de autenticación. Espeja el comportamiento (y los mensajes en
 * español) de las rutas /api/auth/* del BFF legacy.
 */
@Service
public class AuthService {

	/** Cuentas demo seedeadas en V3__seed_demo.sql. */
	private static final Map<String, String> DEMO_ACCOUNTS = Map.of(
			"doctor", "doctor.demo@sinapsistencia.pe",
			"lawyer", "lawyer.demo@sinapsistencia.pe",
			"admin", "admin.demo@sinapsistencia.pe");

	private final ProfileRepository profileRepository;
	private final DoctorProfileRepository doctorProfileRepository;
	private final LawyerProfileRepository lawyerProfileRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(ProfileRepository profileRepository,
			DoctorProfileRepository doctorProfileRepository,
			LawyerProfileRepository lawyerProfileRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService) {
		this.profileRepository = profileRepository;
		this.doctorProfileRepository = doctorProfileRepository;
		this.lawyerProfileRepository = lawyerProfileRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	/** Modo 1: login por email + password. */
	public LoginResponse login(String email, String password) {
		if (email == null || email.isBlank() || password == null || password.isBlank()) {
			throw new BadRequestException("Email y contraseña son requeridos");
		}

		Profile profile = profileRepository.findByEmail(email)
				.orElseThrow(() -> new UnauthorizedException("Credenciales incorrectas"));

		if (!passwordEncoder.matches(password, profile.getPasswordHash())) {
			throw new UnauthorizedException("Credenciales incorrectas");
		}

		if (!profile.isActive()) {
			throw new ForbiddenException("Tu cuenta ha sido desactivada");
		}

		return new LoginResponse(UserDto.from(profile), jwtService.generateToken(profile));
	}

	/** Modo 2: login por rol demo (doctor/lawyer/admin). */
	public LoginResponse loginByRole(String role) {
		String demoEmail = DEMO_ACCOUNTS.get(role);
		if (demoEmail == null) {
			throw new BadRequestException("Rol no válido");
		}

		Profile profile = profileRepository.findByEmail(demoEmail)
				.orElseThrow(() -> new ServiceUnavailableException(
						"Cuenta demo \"" + role + "\" no disponible. Ejecuta el seed de la base de datos."));

		if (!profile.isActive()) {
			throw new ForbiddenException("Tu cuenta ha sido desactivada");
		}

		return new LoginResponse(UserDto.from(profile), jwtService.generateToken(profile));
	}

	/** GET /api/auth/me: perfil fresco desde BD a partir del id del token. */
	public UserDto me(UUID userId) {
		Profile profile = profileRepository.findById(userId)
				.orElseThrow(() -> new NotFoundException("Perfil no encontrado"));
		return UserDto.from(profile);
	}

	/** Registro público (médicos y abogados), con las validaciones y mensajes del legacy. */
	@Transactional
	public void register(RegisterRequest request) {
		if (isBlank(request.name()) || isBlank(request.email())
				|| isBlank(request.password()) || isBlank(request.role())) {
			throw new BadRequestException("Todos los campos son requeridos");
		}

		if (!"doctor".equals(request.role()) && !"lawyer".equals(request.role())) {
			throw new BadRequestException("Rol no válido");
		}

		if (request.password().length() < 8) {
			throw new BadRequestException("La contraseña debe tener al menos 8 caracteres");
		}

		if ("doctor".equals(request.role()) && isBlank(request.specialty())) {
			throw new BadRequestException("La especialidad médica es requerida");
		}

		if ("lawyer".equals(request.role())) {
			if (request.legalSpecialties() == null || request.legalSpecialties().isEmpty()) {
				throw new BadRequestException("Selecciona al menos una especialidad legal");
			}
			if (request.medicalAreas() == null || request.medicalAreas().isEmpty()) {
				throw new BadRequestException("Selecciona al menos un área médica de interés");
			}
		}

		if (profileRepository.existsByEmail(request.email())) {
			throw new ConflictException("El correo electrónico ya está registrado");
		}

		UserRole role = UserRole.fromValue(request.role());
		Profile profile = new Profile(request.email(), request.name(), role,
				passwordEncoder.encode(request.password()));
		profile = profileRepository.save(profile);

		if (role == UserRole.DOCTOR) {
			DoctorProfile doctorProfile = new DoctorProfile(
					profile,
					request.cmp() == null ? "" : request.cmp(),
					request.specialty());
			doctorProfile.setHospital(request.hospital() == null ? "No especificado" : request.hospital());
			doctorProfileRepository.save(doctorProfile);
		} else {
			LawyerProfile lawyerProfile = new LawyerProfile(
					profile,
					request.cab() == null ? "" : request.cab());
			lawyerProfile.setSpecialties(request.legalSpecialties());
			lawyerProfile.setMedicalAreas(request.medicalAreas());
			lawyerProfileRepository.save(lawyerProfile);
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
