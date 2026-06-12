package pe.sinapsistencia.profile.web;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import pe.sinapsistencia.audit.infrastructure.Auditable;
import pe.sinapsistencia.auth.security.AuthenticatedUser;
import pe.sinapsistencia.auth.web.dto.MessageResponse;
import pe.sinapsistencia.profile.application.ProfileService;
import pe.sinapsistencia.profile.web.dto.ProfileResponse;
import pe.sinapsistencia.profile.web.dto.UpdateProfileRequest;
import pe.sinapsistencia.shared.api.ApiResponse;

/** Perfil propio: GET/PUT/PATCH /api/profile + POST /api/profile/avatar (Cloudinary). */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

	private final ProfileService profileService;

	public ProfileController(ProfileService profileService) {
		this.profileService = profileService;
	}

	@GetMapping
	public ApiResponse<ProfileResponse> get(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) String userId) {
		return ApiResponse.ok(profileService.get(user, userId));
	}

	@PatchMapping
	@Auditable(action = "update", resource = "profile")
	public ApiResponse<MessageResponse> patch(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestBody UpdateProfileRequest request) {
		profileService.update(user, request);
		return ApiResponse.ok(new MessageResponse("Perfil actualizado correctamente"));
	}

	/** El inventario de endpoints lista PUT además del PATCH legacy; mismo comportamiento. */
	@PutMapping
	@Auditable(action = "update", resource = "profile")
	public ApiResponse<MessageResponse> put(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestBody UpdateProfileRequest request) {
		return patch(user, request);
	}

	@PostMapping("/avatar")
	@Auditable(action = "update", resource = "profile")
	public ApiResponse<Map<String, String>> uploadAvatar(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam("file") MultipartFile file,
			@RequestParam(value = "userId", required = false) String userId) {
		String avatarUrl = profileService.uploadAvatar(user, userId, file);
		return ApiResponse.ok(Map.of("avatarUrl", avatarUrl));
	}
}
