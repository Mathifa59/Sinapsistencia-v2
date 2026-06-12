package pe.sinapsistencia.auth.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import pe.sinapsistencia.auth.domain.UserRole;

/**
 * Lee el JWT (cookie httpOnly {@code access_token} o header {@code Authorization: Bearer})
 * y puebla el SecurityContext con ROLE_DOCTOR / ROLE_LAWYER / ROLE_ADMIN.
 * Token inválido o ausente → la request sigue sin autenticación (401 en rutas protegidas).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

	public static final String ACCESS_TOKEN_COOKIE = "access_token";

	private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

	private final JwtService jwtService;

	public JwtAuthFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String token = resolveToken(request);

		if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			try {
				Claims claims = jwtService.parseToken(token);
				UserRole role = UserRole.fromValue(claims.get("role", String.class));
				AuthenticatedUser principal = new AuthenticatedUser(
						UUID.fromString(claims.getSubject()),
						claims.get("email", String.class),
						claims.get("name", String.class),
						role);

				var authentication = new UsernamePasswordAuthenticationToken(
						principal, null,
						List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
				authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} catch (Exception ex) {
				log.debug("Token JWT inválido: {}", ex.getMessage());
			}
		}

		filterChain.doFilter(request, response);
	}

	private String resolveToken(HttpServletRequest request) {
		if (request.getCookies() != null) {
			for (Cookie cookie : request.getCookies()) {
				if (ACCESS_TOKEN_COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
					return cookie.getValue();
				}
			}
		}
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			return header.substring(7);
		}
		return null;
	}
}
