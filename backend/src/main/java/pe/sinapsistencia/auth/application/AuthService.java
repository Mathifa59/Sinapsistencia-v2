package pe.sinapsistencia.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.sinapsistencia.auth.domain.EmailVerificationToken;
import pe.sinapsistencia.auth.domain.PasswordResetToken;
import pe.sinapsistencia.auth.domain.Profile;
import pe.sinapsistencia.auth.domain.UserConsent;
import pe.sinapsistencia.auth.domain.UserRole;
import pe.sinapsistencia.auth.infrastructure.EmailVerificationTokenRepository;
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
import pe.sinapsistencia.shared.exception.EmailNotVerifiedException;
import pe.sinapsistencia.shared.exception.ForbiddenException;
import pe.sinapsistencia.shared.exception.NotFoundException;
import pe.sinapsistencia.shared.exception.UnauthorizedException;

/**
 * Casos de uso de autenticación. Espeja el comportamiento (y los mensajes en
 * español) de las rutas /api/auth/* del BFF legacy.
 */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	/** Vigencia del enlace de confirmación de correo. */
	static final Duration VERIFICATION_TTL = Duration.ofHours(24);
	/** Espera mínima entre dos envíos del enlace de confirmación al mismo perfil. */
	static final Duration VERIFICATION_RESEND_COOLDOWN = Duration.ofSeconds(60);

	private final ProfileRepository profileRepository;
	private final DoctorProfileRepository doctorProfileRepository;
	private final LawyerProfileRepository lawyerProfileRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final EmailVerificationTokenRepository emailVerificationTokenRepository;
	private final UserConsentRepository userConsentRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final MailNotifier mailNotifier;
	private final LoginAttemptService loginAttemptService;
	private final boolean logResetToken;
	private final SecureRandom secureRandom = new SecureRandom();

	public AuthService(ProfileRepository profileRepository,
			DoctorProfileRepository doctorProfileRepository,
			LawyerProfileRepository lawyerProfileRepository,
			PasswordResetTokenRepository passwordResetTokenRepository,
			EmailVerificationTokenRepository emailVerificationTokenRepository,
			UserConsentRepository userConsentRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			MailNotifier mailNotifier,
			LoginAttemptService loginAttemptService,
			@Value("${app.auth.log-reset-token:false}") boolean logResetToken) {
		this.profileRepository = profileRepository;
		this.doctorProfileRepository = doctorProfileRepository;
		this.lawyerProfileRepository = lawyerProfileRepository;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.emailVerificationTokenRepository = emailVerificationTokenRepository;
		this.userConsentRepository = userConsentRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.mailNotifier = mailNotifier;
		this.loginAttemptService = loginAttemptService;
		this.logResetToken = logResetToken;
	}

	/** Login por email + password (con protección de fuerza bruta, 429). */
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

		// Se comprueba DESPUÉS de la contraseña: quien no la conoce no averigua si el correo existe.
		if (!profile.isEmailVerified()) {
			throw new EmailNotVerifiedException();
		}

		loginAttemptService.reset(email);
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

		// La cuenta nace sin confirmar: el enlace prueba que el titular controla el correo.
		// La bienvenida se envía recién al confirmar (verifyEmail).
		issueVerificationToken(profile);
	}

	/**
	 * Confirma el correo con el token recibido por correo. Un solo uso, con caducidad,
	 * y ligado al perfil cuyo correo coincide con el indicado.
	 */
	@Transactional
	public void verifyEmail(String email, String token) {
		if (isBlank(email) || isBlank(token)) {
			throw new BadRequestException("Correo y token son requeridos");
		}

		EmailVerificationToken stored = emailVerificationTokenRepository
				.findByTokenHashAndUsedAtIsNull(hashToken(token.trim()))
				.orElseThrow(() -> new BadRequestException("Enlace inválido o expirado"));
		if (stored.getExpiresAt().isBefore(Instant.now())) {
			throw new BadRequestException("Enlace inválido o expirado");
		}

		Profile profile = profileRepository.findById(stored.getProfileId())
				.orElseThrow(() -> new BadRequestException("Enlace inválido o expirado"));
		if (!profile.getEmail().equalsIgnoreCase(email.trim())) {
			throw new BadRequestException("Enlace inválido o expirado");
		}

		stored.markUsed();
		emailVerificationTokenRepository.save(stored);

		if (!profile.isEmailVerified()) {
			profile.markEmailVerified();
			profileRepository.save(profile);
			mailNotifier.sendWelcome(profile.getEmail(), profile.getName(),
					profile.getRole() == UserRole.DOCTOR ? "Médico" : "Abogado");
		}
	}

	/**
	 * Reenvía el enlace de confirmación. La respuesta es siempre la misma exista o no la
	 * cuenta (no revela qué correos están registrados) y hay una espera mínima entre envíos.
	 */
	@Transactional
	public String resendVerification(String email) {
		if (isBlank(email)) {
			throw new BadRequestException("El correo electrónico es requerido");
		}
		Profile profile = profileRepository.findByEmail(email.trim()).orElse(null);
		if (profile != null && profile.isActive() && !profile.isEmailVerified()) {
			issueVerificationTokenRespectingCooldown(profile);
		}
		return "Si el correo está registrado y pendiente de confirmar, recibirás un nuevo enlace.";
	}

	private void issueVerificationTokenRespectingCooldown(Profile profile) {
		boolean tooSoon = emailVerificationTokenRepository
				.findFirstByProfileIdOrderByCreatedAtDesc(profile.getId())
				.map(last -> last.getCreatedAt() != null
						&& last.getCreatedAt().plus(VERIFICATION_RESEND_COOLDOWN).isAfter(Instant.now()))
				.orElse(false);
		if (tooSoon) {
			log.info("[auth] Reenvío de confirmación omitido por espera mínima: {}", profile.getEmail());
			return;
		}
		issueVerificationToken(profile);
	}

	/** Invalida los enlaces anteriores, crea uno nuevo y lo envía (o lo registra en el log en local). */
	private void issueVerificationToken(Profile profile) {
		emailVerificationTokenRepository.findByProfileIdAndUsedAtIsNull(profile.getId())
				.forEach(old -> {
					old.markUsed();
					emailVerificationTokenRepository.save(old);
				});

		String token = newToken();
		emailVerificationTokenRepository.save(new EmailVerificationToken(
				profile.getId(), hashToken(token), Instant.now().plus(VERIFICATION_TTL)));

		if (mailNotifier.isConfigured()) {
			mailNotifier.sendEmailVerification(profile.getEmail(), profile.getName(), token);
		} else if (logResetToken) {
			// Solo perfil local: sin Resend el desarrollador toma el token del log del servidor.
			log.warn("[auth][local] Sin RESEND_API_KEY -- token de confirmación de {}: {}", profile.getEmail(), token);
		} else {
			// Alcanzable solo en el perfil de pruebas: fuera de local/test la app no arranca sin Resend.
			log.warn("[auth] Sin RESEND_API_KEY -- no se envió el enlace de confirmación de {}", profile.getEmail());
		}
	}

	private String newToken() {
		byte[] bytes = new byte[24];
		secureRandom.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	/** SHA-256 en hex: en BD solo vive el hash del token de confirmación. */
	static String hashToken(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 no disponible", ex);
		}
	}

	/**
	 * HU-04: solicitud de restablecimiento. El token nunca viaja en la respuesta
	 * HTTP: va por correo; solo con {@code app.auth.log-reset-token} (perfil
	 * {@code local}) se imprime en el log del servidor.
	 */
	@Transactional
	public ForgotPasswordResponse forgotPassword(String email) {
		if (email == null || email.isBlank()) {
			throw new BadRequestException("El correo electrónico es requerido");
		}

		String message = "Si el correo está registrado, recibirás instrucciones para restablecer tu contraseña.";
		Profile profile = profileRepository.findByEmail(email.trim()).orElse(null);
		if (profile == null || !profile.isActive()) {
			return new ForgotPasswordResponse(message);
		}

		// El token de recuperación solo se entrega a un correo ya confirmado. Si aún no lo está,
		// se reenvía el enlace de confirmación (misma respuesta: no revela el estado de la cuenta).
		if (!profile.isEmailVerified()) {
			issueVerificationTokenRespectingCooldown(profile);
			return new ForgotPasswordResponse(message);
		}

		String token = newToken();

		PasswordResetToken resetToken = new PasswordResetToken(
				profile.getId(),
				profile.getEmail(),
				token,
				Instant.now().plus(1, ChronoUnit.HOURS));
		passwordResetTokenRepository.save(resetToken);

		if (mailNotifier.isConfigured()) {
			mailNotifier.sendPasswordReset(profile.getEmail(), profile.getName(), token);
		} else if (logResetToken) {
			// Solo perfil local: sin Resend el desarrollador toma el token del log del servidor.
			log.warn("[auth][local] Sin RESEND_API_KEY -- token de recuperación de {}: {}", profile.getEmail(), token);
		} else {
			// Alcanzable solo en el perfil de pruebas: fuera de local/test la app no arranca sin Resend.
			log.warn("[auth] Sin RESEND_API_KEY -- no se envió el token de recuperación de {}", profile.getEmail());
		}

		return new ForgotPasswordResponse(message);
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

		// El token está atado al perfil que lo pidió y su correo debe seguir confirmado.
		if (!profile.isEmailVerified()
				|| (resetToken.getProfileId() != null && !resetToken.getProfileId().equals(profile.getId()))) {
			throw new BadRequestException("Token inválido o expirado");
		}

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
