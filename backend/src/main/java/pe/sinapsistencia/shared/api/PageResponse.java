package pe.sinapsistencia.shared.api;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Respuesta paginada (espeja el {@code .range(from, to)} + count de Supabase:
 * lista de items + total de filas).
 */
public record PageResponse<T>(List<T> items, long total) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getTotalElements());
	}

	public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getTotalElements());
	}
}
