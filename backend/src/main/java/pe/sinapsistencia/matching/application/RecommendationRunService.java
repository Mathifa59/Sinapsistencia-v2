package pe.sinapsistencia.matching.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.sinapsistencia.auth.domain.Profile;
import pe.sinapsistencia.cases.domain.LegalCase;
import pe.sinapsistencia.matching.domain.MatchRecommendation;
import pe.sinapsistencia.matching.domain.RecommendationRun;
import pe.sinapsistencia.matching.domain.RecommendationRunStatus;
import pe.sinapsistencia.matching.infrastructure.MatchRecommendationRepository;
import pe.sinapsistencia.matching.infrastructure.RecommendationRunRepository;
import pe.sinapsistencia.shared.exception.ConflictException;

/**
 * H-02: adquisición/finalización de ejecuciones de matching con sus propios
 * límites transaccionales -- separado de {@link RecommendationService} a
 * propósito para usar {@link TransactionTemplate} en vez de {@code @Transactional}:
 * llamar estos métodos desde otro método {@code @Transactional} de la MISMA
 * clase no abriría una transacción nueva (self-invocation ignora el proxy de
 * Spring), por lo que la adquisición corta y la finalización atómica no
 * quedarían realmente aisladas una de otra ni del fallo intermedio.
 */
@Service
public class RecommendationRunService {

	private static final Logger log = LoggerFactory.getLogger(RecommendationRunService.class);

	/** Una ejecución 'processing' sin avanzar más de esto se considera abandonada (proceso interrumpido). */
	private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(2);

	private final RecommendationRunRepository runRepository;
	private final MatchRecommendationRepository matchRecommendationRepository;
	private final TransactionTemplate transactionTemplate;
	private final ObjectMapper objectMapper;

	public RecommendationRunService(RecommendationRunRepository runRepository,
			MatchRecommendationRepository matchRecommendationRepository,
			PlatformTransactionManager transactionManager,
			ObjectMapper objectMapper) {
		this.runRepository = runRepository;
		this.matchRecommendationRepository = matchRecommendationRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.objectMapper = objectMapper;
	}

	public record AcquireResult(RecommendationRun run, boolean reused) {
	}

	/**
	 * Inserta una fila {@code processing} en una transacción corta. Ante colisión
	 * de {@code (doctor_id, idempotency_key)}, resuelve la fila existente FUERA de
	 * esa transacción fallida (ya hizo rollback). No invoca ML: el llamador hace
	 * eso después de adquirir, con la fila ya confirmada en BD.
	 */
	public AcquireResult acquire(Profile doctor, LegalCase legalCase, String idempotencyKey, String requestHash,
			int topK, String pipelineVersion) {
		try {
			RecommendationRun created = transactionTemplate.execute(status -> {
				RecommendationRun run = new RecommendationRun(doctor, legalCase, idempotencyKey, requestHash,
						topK, pipelineVersion);
				return runRepository.saveAndFlush(run);
			});
			return new AcquireResult(created, false);
		} catch (DataIntegrityViolationException ex) {
			RecommendationRun existing = runRepository.findByDoctor_IdAndIdempotencyKey(doctor.getId(), idempotencyKey)
					.orElseThrow(() -> new IllegalStateException(
							"Colisión de idempotencia sin fila existente legible", ex));
			return resolveExisting(existing, requestHash);
		}
	}

	private AcquireResult resolveExisting(RecommendationRun existing, String requestHash) {
		if (existing.getStatus() == RecommendationRunStatus.COMPLETED) {
			if (existing.getRequestHash().equals(requestHash)) {
				return new AcquireResult(existing, true);
			}
			throw new ConflictException(
					"Ya existe una ejecución completada con esta clave de idempotencia para un comando distinto; usa una clave nueva.");
		}
		if (existing.getStatus() == RecommendationRunStatus.PROCESSING) {
			if (isStale(existing)) {
				markFailed(existing.getId(), "stale_processing",
						"La ejecución anterior con esta clave no completó dentro del tiempo esperado (proceso interrumpido).");
				throw new ConflictException(
						"La ejecución anterior con esta clave quedó inconclusa; usa una clave nueva para reintentar.");
			}
			throw new ConflictException("Ya hay una generación de recomendaciones en curso con esta clave.");
		}
		// FAILED
		throw new ConflictException(
				"La ejecución anterior con esta clave falló (" + existing.getErrorMessage()
						+ "); usa una clave nueva para una nueva generación.");
	}

	private boolean isStale(RecommendationRun run) {
		Instant reference = run.getUpdatedAt() != null ? run.getUpdatedAt() : run.getCreatedAt();
		return reference != null && reference.isBefore(Instant.now().minus(PROCESSING_TIMEOUT));
	}

	/** Resultado crudo de {@code RecommendationService.computeViaMlOrFallback}, sin IDs ni persistencia. */
	public record ComputedItem(
			UUID lawyerUserId,
			BigDecimal scoreRaw,
			BigDecimal contentScoreRaw,
			BigDecimal performanceScoreRaw,
			List<String> matchedSpecialties,
			JsonNode featureImportance,
			List<String> reasons,
			JsonNode lawyerSnapshot) {
	}

	public record ComputedResult(
			List<ComputedItem> items,
			String origin,
			String modelVersion,
			JsonNode weights,
			JsonNode modelInfo,
			JsonNode inputSnapshot,
			JsonNode corpusSnapshot,
			String inputHash,
			String corpusHash,
			int candidateCount) {
	}

	/**
	 * Finaliza la ejecución: persiste las filas {@link MatchRecommendation} (rank
	 * desde la posición ya ordenada de {@code result.items()}) y marca el run
	 * {@code completed} en UNA sola transacción atómica.
	 */
	public RecommendationRun complete(UUID runId, ComputedResult result, Map<UUID, Profile> lawyerUserById) {
		return transactionTemplate.execute(status -> {
			RecommendationRun run = runRepository.findById(runId)
					.orElseThrow(() -> new IllegalStateException("Ejecución no encontrada al finalizar: " + runId));

			int rank = 1;
			for (ComputedItem item : result.items()) {
				Profile lawyerProfile = lawyerUserById.get(item.lawyerUserId());
				if (lawyerProfile == null) {
					continue;
				}
				MatchRecommendation entity = new MatchRecommendation(run.getDoctor(), lawyerProfile,
						toPercent(item.scoreRaw()));
				entity.setLegalCase(run.getLegalCase());
				entity.setRun(run);
				entity.setRank(rank++);
				entity.setScoreRaw(item.scoreRaw());
				entity.setContentScoreRaw(item.contentScoreRaw());
				entity.setPerformanceScoreRaw(item.performanceScoreRaw());
				entity.setLawyerSnapshot(item.lawyerSnapshot());
				entity.setMatchedSpecialties(item.matchedSpecialties());
				entity.setReasons(item.reasons());
				entity.setFactors(item.featureImportance() == null ? "[]" : item.featureImportance().toString());
				entity.setAlgorithmVersion(result.modelVersion() != null ? result.modelVersion() : result.origin());
				matchRecommendationRepository.save(entity);
			}

			run.setStatus(RecommendationRunStatus.COMPLETED);
			run.setOrigin(result.origin());
			run.setModelVersion(result.modelVersion());
			run.setWeights(result.weights());
			run.setModelInfo(result.modelInfo());
			run.setInputSnapshot(result.inputSnapshot());
			run.setCorpusSnapshot(result.corpusSnapshot());
			run.setInputHash(result.inputHash());
			run.setCorpusHash(result.corpusHash());
			run.setCandidateCount(result.candidateCount());
			run.setResultCount(rank - 1);
			run.setCompletedAt(Instant.now());
			return runRepository.save(run);
		});
	}

	/** Registra el fallo en una transacción INDEPENDIENTE -- persiste aunque la transacción de generación haya hecho rollback. */
	public RecommendationRun markFailed(UUID runId, String errorCode, String errorMessage) {
		log.warn("Ejecución de matching {} marcada como failed ({}): {}", runId, errorCode, errorMessage);
		return transactionTemplate.execute(status -> {
			RecommendationRun run = runRepository.findById(runId)
					.orElseThrow(() -> new IllegalStateException("Ejecución no encontrada al marcar fallo: " + runId));
			run.setStatus(RecommendationRunStatus.FAILED);
			run.setErrorCode(errorCode);
			run.setErrorMessage(errorMessage);
			return runRepository.save(run);
		});
	}

	private BigDecimal toPercent(BigDecimal raw) {
		return raw.multiply(BigDecimal.valueOf(100)).setScale(2, java.math.RoundingMode.HALF_UP);
	}

	JsonNode toJson(Object value) {
		return objectMapper.valueToTree(value);
	}
}
