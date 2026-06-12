package pe.sinapsistencia.auth.web.dto;

import java.util.List;

/**
 * Body de POST /api/auth/register (registro público de médicos y abogados).
 * Base: name, email, password, role. Doctor: + cmp?, specialty, hospital?.
 * Lawyer: + cab?, legalSpecialties, medicalAreas.
 */
public record RegisterRequest(
		String name,
		String email,
		String password,
		String role,
		String cmp,
		String specialty,
		String hospital,
		String cab,
		List<String> legalSpecialties,
		List<String> medicalAreas) {
}
