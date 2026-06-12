package pe.sinapsistencia.auth.web;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.sinapsistencia.audit.infrastructure.Auditable;
import pe.sinapsistencia.auth.application.AuthService;
import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.auth.security.JwtAuthFilter;
import pe.sinapsistencia.auth.security.JwtService;
import pe.sinapsistencia.auth.web.dto.LoginRequest;
import pe.sinapsistencia.auth.web.dto.LoginResponse;
import pe.sinapsistencia.auth.web.dto.MessageResponse;
import pe.sinapsistencia.auth.web.dto.RegisterRequest;
import pe.sinapsistencia.auth.web.dto.UserDto;
import pe.sinapsistencia.shared.api.ApiResponse;
import pe.sinapsistencia.shared.exception.UnauthorizedException;

/**
 * Espeja /api/auth/* del BFF legacy: login (email+password y modo demo por rol),
 * logout, me y register. El JWT viaja en cookie httpOnly `access_token` y
 * también en el body (igual que el token de Supabase en el legacy).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final JwtService jwtService;
	private final boolean cookieSecure;

	public AuthController(AuthService authService, JwtService jwtService,
			@Value("${app.jwt.cookie-secure:false}") boolean cookieSecure) {
		this.authService = authService;
		this.jwtService = jwtService;
		this.cookieSecure = cookieSecure;
	}

	@PostMapping("/login")
	@Auditable(action = "login", resource = "auth")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
		LoginResponse result = request.role() != null
				? authService.loginByRole(request.role())
				: authService.login(request.email(), request.password());

		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, buildAccessTokenCookie(result.token()).toString())
				.body(ApiResponse.ok(result));
	}

	@PostMapping("/logout")
	@Auditable(action = "logout", resource = "auth")
	public ResponseEntity<ApiResponse<MessageResponse>> logout() {
		ResponseCookie expired = ResponseCookie.from(JwtAuthFilter.ACCESS_TOKEN_COOKIE, "")
				.httpOnly(true)
				.secure(cookieSecure)
				.path("/")
				.sameSite("Lax")
				.maxAge(0)
				.build();

		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, expired.toString())
				.body(ApiResponse.ok(new MessageResponse("Sesión cerrada correctamente")));
	}

	@GetMapping("/me")
	public ApiResponse<UserDto> me(@AuthenticationPrincipal AuthenticatedUser user) {
		if (user == null) {
			throw new UnauthorizedException("No autorizado");
		}
		return ApiResponse.ok(authService.me(user.id()));
	}

	@PostMapping("/register")
	public ResponseEntity<ApiResponse<MessageResponse>> register(@RequestBody RegisterRequest request) {
		authService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(new MessageResponse("Cuenta creada exitosamente. Ya puedes iniciar sesión.")));
	}

	private ResponseCookie buildAccessTokenCookie(String token) {
		return ResponseCookie.from(JwtAuthFilter.ACCESS_TOKEN_COOKIE, token)
				.httpOnly(true)
				.secure(cookieSecure)
				.path("/")
				.sameSite("Lax")
				.maxAge(Duration.ofMillis(jwtService.getExpirationMs()))
				.build();
	}
}
