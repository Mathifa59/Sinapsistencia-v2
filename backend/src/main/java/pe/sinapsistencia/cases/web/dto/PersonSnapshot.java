package pe.sinapsistencia.cases.web.dto;

import pe.sinapsistencia.auth.domain.Profile;

/** Snapshot aplanado de un perfil en respuestas de consulta (igual que el legacy: {id, fullName, email}). */
public record PersonSnapshot(String id, String fullName, String email) {

	public static PersonSnapshot from(Profile profile) {
		return profile == null ? null
				: new PersonSnapshot(profile.getId().toString(), profile.getName(), profile.getEmail());
	}
}
