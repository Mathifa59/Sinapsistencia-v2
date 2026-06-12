package pe.sinapsistencia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import pe.sinapsistencia.auth.security.JwtAuthFilter;

/**
 * Seguridad stateless con JWT: JwtAuthFilter valida el token (cookie httpOnly
 * o Bearer) y puebla el SecurityContext con ROLE_DOCTOR/LAWYER/ADMIN.
 * La autorización fina por recurso (ownership) vive en los services (FASE 4),
 * espejando lo que RLS hacía en Supabase.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								"/actuator/health",
								"/v3/api-docs/**",
								"/swagger-ui/**",
								"/swagger-ui.html",
								"/api/auth/**",
								"/api/ml/health")
						.permitAll()
						.anyRequest().authenticated())
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
				// Sin token: 401 con cuerpo vacío (el handler de la app pone el sobre
				// {success:false} en los errores que pasan por los controllers).
				.exceptionHandling(ex -> ex.authenticationEntryPoint(
						(request, response, authException) -> response.setStatus(HttpStatus.UNAUTHORIZED.value())));
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
