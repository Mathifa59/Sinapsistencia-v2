package pe.sinapsistencia.auth.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum UserRole {

	DOCTOR("doctor"),
	LAWYER("lawyer"),
	ADMIN("admin");

	private final String value;

	UserRole(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public static UserRole fromValue(String value) {
		for (UserRole role : values()) {
			if (role.value.equals(value)) {
				return role;
			}
		}
		throw new IllegalArgumentException("Rol desconocido: " + value);
	}

	@Converter(autoApply = true)
	public static class UserRoleConverter implements AttributeConverter<UserRole, String> {

		@Override
		public String convertToDatabaseColumn(UserRole attribute) {
			return attribute == null ? null : attribute.getValue();
		}

		@Override
		public UserRole convertToEntityAttribute(String dbData) {
			return dbData == null ? null : fromValue(dbData);
		}
	}
}
