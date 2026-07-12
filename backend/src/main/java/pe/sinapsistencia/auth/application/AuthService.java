package pe.sinapsistencia.auth.application;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.sinapsistencia.auth.domain.PasswordResetToken;
import pe.sinapsistencia.auth.domain.Profile;
import pe.sinapsistencia.auth.domain.UserConsent;
import pe.sinapsistencia.auth.domain.UserRole;
import pe.sinapsistencia.auth.infrastructure.PasswordResetTokenRepository;
import pe.sinapsistencia.auth.infrastructure.ProfileRepository;
import pe.sinapsistencia.auth.infrastructure.UserConsentRepository;
import pe.sinapsistencia.auth.security.JwtService;
import pe.sinapsistencia.auth.web.dto.ForgotPasswordResponse;
import pe.sinapsistencia.auth.web.dto.LoginResponse;
import pe.sinapsistencia.auth.web.dto.RegisterRequest;
import pe.sinapsistencia.auth.web.dto.UserDto;
import pe.sinapsistencia.notifications.MailNotifier;
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

	/**
	 * Correos de las cuentas demo (login por rol). Se toman de la configuración
	 * para que coincidan con los correos reales que aplica DemoAccountEmailConfigurer
	 * en producción; por defecto son los del seed (V3__seed_demo.sql).
	 */
	private final Map<String, String> demoAccounts;

	private final ProfileRepository profileRepository;
	private final DoctorProfileRepository doctorProfileRepository;
	private final LawyerProfileRepository lawyerProfileRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final UserConsentRepository userConsentRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final MailNotifier mailNotifier;
	private final LoginAttemptService loginAttemptService;
	private final SecureRandom secureRandom = new SecureRandom();

	public AuthService(ProfileRepository profileRepository,
			DoctorProfileRepository doctorProfileRepository,
			LawyerProfileRepository lawyerProfileRepository,
			PasswordResetTokenRepository passwordResetTokenRepository,
			UserConsentRepository userConsentRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			MailNotifier mailNotifier,
			LoginAttemptService loginAttemptService,
			@Value("${app.demo.doctor-email:doctor.demo@sinapsistencia.pe}") String doctorEmail,
			@Value("${app.demo.lawyer-email:lawyer.demo@sinapsistencia.pe}") String lawyerEmail,
			@Value("${app.demo.admin-email:admin.demo@sinapsistencia.pe}") String adminEmail) {
		this.profileRepository = profileRepository;
		this.doctorProfileRepository = doctorProfileRepository;
		this.lawyerProfileRepository = lawyerProfileRepository;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.userConsentRepository = userConsentRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.mailNotifier = mailNotifier;
		this.loginAttemptService = loginAttemptService;
		this.demoAccounts = Map.of(
				"doctor", doctorEmail,
				"lawyer", lawyerEmail,
				"admin", adminEmail);
	}

	/** Modo 1: login por email + password (con protección de fuerza bruta, 429). */
	public LoginResponse login(String email, String password) {
		if (email == null || email.isBlank() || password == null || password.isBlank()) {
			throw new BadRequestException("Email y contraseña son requeridos");
		}

		loginAttemptService.assertNotBlocked(email);

		Profile profile = profileRepository.findByEmail(email).orElse(null);
		if (profile == null || !passwordEncoder.matches(password, profile.getPasswordHash())) {
			loginAttemptService.recordFailure(email);
			throw new UnauthorizedException("Credenciales incorrectas");
		}

		if (!profile.isActive()) {
			throw new ForbiddenException("Tu cuenta ha sido desactivada");
		}

		loginAttemptService.reset(email);
		return new LoginResponse(UserDto.from(profile), jwtService.generateToken(profile));
	}

	/** Modo 2: login por rol demo (doctor/lawyer/admin). */
	public LoginResponse loginByRole(String role) {
		String demoEmail = demoAccounts.get(role);
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

		// Ley 29733: el consentimiento es requisito y queda registrado con versión y fecha.
		if (!Boolean.TRUE.equals(request.acceptPrivacyPolicy())) {
			throw new BadRequestException(
					"Debes aceptar la política de privacidad y el tratamiento de datos personales (Ley 29733)");
		}

		if (profileRepository.existsByEmail(request.email())) {
			throw new ConflictException("El correo electrónico ya está registrado");
		}

		UserRole role = UserRole.fromValue(request.role());
		Profile profile = new Profile(request.email(), request.name(), role,
				passwordEncoder.encode(request.password()));
		profile = profileRepository.save(profile);

		userConsentRepository.save(new UserConsent(profile,
				UserConsent.TYPE_PRIVACY_POLICY, UserConsent.CURRENT_POLICY_VERSION));

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

		// Correo de bienvenida (fire-and-forget vía n8n; no bloquea el registro).
		mailNotifier.sendWelcome(profile.getEmail(), profile.getName(),
				role == UserRole.DOCTOR ? "Médico" : "Abogado");
	}

	/** HU-04: solicitud de restablecimiento (en prototipo devuelve token para demo). */
	@Transactional
	public ForgotPasswordResponse forgotPassword(String email) {
		if (email == null || email.isBlank()) {
			throw new BadRequestException("El correo electrónico es requerido");
		}

		String message = "Si el correo está registrado, recibirás instrucciones para restablecer tu contraseña.";
		Profile profile = profileRepository.findByEmail(email.trim()).orElse(null);
		if (profile == null || !profile.isActive()) {
			return new ForgotPasswordResponse(message, null);
		}

		byte[] bytes = new byte[24];
		secureRandom.nextBytes(bytes);
		String token = HexFormat.of().formatHex(bytes);

		PasswordResetToken resetToken = new PasswordResetToken(
				profile.getEmail(),
				token,
				Instant.now().plus(1, ChronoUnit.HOURS));
		passwordResetTokenRepository.save(resetToken);

		// Con n8n configurado el token viaja por correo y NO se expone en la respuesta.
		// Sin n8n (dev local) se devuelve como fallback para no romper el flujo.
		if (mailNotifier.isConfigured()) {
			mailNotifier.sendPasswordReset(profile.getEmail(), profile.getName(), token);
			return new ForgotPasswordResponse(message, null);
		}

		return new ForgotPasswordResponse(
				message + " (prototipo: usa el token mostrado para continuar)",
				token);
	}

	@Transactional
	public void resetPassword(String email, String token, String newPassword) {
		if (isBlank(email) || isBlank(token) || isBlank(newPassword)) {
			throw new BadRequestException("Correo, token y nueva contraseña son requeridos");
		}
		if (newPassword.length() < 8) {
			throw new BadRequestException("La contraseña debe tener al menos 8 caracteres");
		}

		PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenAndUsedFalse(token.trim())
				.orElseThrow(() -> new BadRequestException("Token inválido o expirado"));

		if (!resetToken.getEmail().equalsIgnoreCase(email.trim())) {
			throw new BadRequestException("Token inválido o expirado");
		}
		if (resetToken.getExpiresAt().isBefore(Instant.now())) {
			throw new BadRequestException("Token inválido o expirado");
		}

		Profile profile = profileRepository.findByEmail(email.trim())
				.orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

		profile.setPasswordHash(passwordEncoder.encode(newPassword));
		profileRepository.save(profile);

		resetToken.setUsed(true);
		passwordResetTokenRepository.save(resetToken);
	}

	@Transactional
	public void changePassword(UUID userId, String currentPassword, String newPassword) {
		if (isBlank(currentPassword) || isBlank(newPassword)) {
			throw new BadRequestException("Contraseña actual y nueva son requeridas");
		}
		if (newPassword.length() < 8) {
			throw new BadRequestException("La contraseña debe tener al menos 8 caracteres");
		}

		Profile profile = profileRepository.findById(userId)
				.orElseThrow(() -> new NotFoundException("Perfil no encontrado"));

		if (!passwordEncoder.matches(currentPassword, profile.getPasswordHash())) {
			throw new UnauthorizedException("Contraseña actual incorrecta");
		}

		profile.setPasswordHash(passwordEncoder.encode(newPassword));
		profileRepository.save(profile);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
