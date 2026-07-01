package pe.sinapsistencia.cases.web.dto;

import java.time.Instant;
import java.util.List;

/** Reporte consolidado de consulta (HU-39). */
public record CaseReportDto(
		CaseResponse caseData,
		MlClassificationDto classification,
		List<LegalResponseDto> responses,
		List<TimelineEntryDto> timeline,
		List<String> documentTitles,
		String advisoryNote,
		Instant generatedAt) {
}
