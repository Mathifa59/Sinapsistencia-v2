package pe.sinapsistencia.auth.security;

import java.util.UUID;

import pe.sinapsistencia.auth.domain.UserRole;

/** Principal del SecurityContext: identidad mínima extraída del JWT. */
public record AuthenticatedUser(UUID id, String email, String name, UserRole role) {
}
