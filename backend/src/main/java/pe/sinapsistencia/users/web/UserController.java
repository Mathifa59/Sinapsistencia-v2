package pe.sinapsistencia.users.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.sinapsistencia.audit.infrastructure.Auditable;
import pe.sinapsistencia.auth.web.dto.UserDto;
import pe.sinapsistencia.shared.api.ApiResponse;
import pe.sinapsistencia.users.application.UserService;

/** Gestión de usuarios — solo ADMIN (espeja la pantalla /admin/users del legacy). */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

	public record CreateUserRequest(String name, String email, String role, String password) {
	}

	public record UpdateUserRequest(String name, String role) {
	}

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public ApiResponse<List<UserDto>> list(@RequestParam(required = false) String role) {
		return ApiResponse.ok(userService.list(role));
	}

	@PostMapping
	@Auditable(action = "create", resource = "user")
	public ResponseEntity<ApiResponse<UserDto>> create(@RequestBody CreateUserRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(userService.create(
						request.name(), request.email(), request.role(), request.password())));
	}

	@GetMapping("/{id}")
	public ApiResponse<UserDto> get(@PathVariable UUID id) {
		return ApiResponse.ok(userService.get(id));
	}

	@PutMapping("/{id}")
	@Auditable(action = "update", resource = "user")
	public ApiResponse<UserDto> update(@PathVariable UUID id, @RequestBody UpdateUserRequest request) {
		return ApiResponse.ok(userService.update(id, request.name(), request.role()));
	}

	@PatchMapping("/{id}")
	@Auditable(action = "update", resource = "user")
	public ApiResponse<UserDto> toggleActive(@PathVariable UUID id) {
		return ApiResponse.ok(userService.toggleActive(id));
	}
}
