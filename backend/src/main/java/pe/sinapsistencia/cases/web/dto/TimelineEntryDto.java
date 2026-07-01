package pe.sinapsistencia.cases.web.dto;

import java.time.Instant;

/** Entrada unificada de la línea de tiempo (HU-27). */
public record TimelineEntryDto(
		String id,
		String type,
		String title,
		String description,
		Instant occurredAt,
		String actorName) {
}
