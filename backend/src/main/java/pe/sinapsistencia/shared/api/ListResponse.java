package pe.sinapsistencia.shared.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Shape de los listados del BFF legacy:
 * {@code {data:[...], total, page, pageSize, totalPages}} (cases/documents) o
 * {@code {data:[...], total}} (audit — los campos de página se omiten por NON_NULL).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListResponse<T>(List<T> data, long total, Integer page, Integer pageSize, Integer totalPages) {

	public static <T> ListResponse<T> paged(List<T> data, long total, int page, int pageSize) {
		return new ListResponse<>(data, total, page, pageSize, (int) Math.ceil((double) total / pageSize));
	}

	public static <T> ListResponse<T> simple(List<T> data, long total) {
		return new ListResponse<>(data, total, null, null, null);
	}
}
