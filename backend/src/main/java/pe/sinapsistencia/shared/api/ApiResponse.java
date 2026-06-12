package pe.sinapsistencia.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Sobre de respuesta de toda la API. Contrato idéntico al BFF legacy:
 * {@code { success: true, data }} / {@code { success: false, error }}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, String error) {

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static <T> ApiResponse<T> fail(String error) {
		return new ApiResponse<>(false, null, error);
	}
}
