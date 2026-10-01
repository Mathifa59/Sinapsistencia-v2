package pe.sinapsistencia.notifications.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.notifications.domain.NotificationOutbox;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

	Optional<NotificationOutbox> findByEventKey(String eventKey);

	List<NotificationOutbox> findByCaseIdOrderByCreatedAtDesc(UUID caseId);

	/** H-06: candidatos para el worker -- pendientes listos o "processing" con lease vencido (recuperación). */
	@Query("SELECT n.id FROM NotificationOutbox n WHERE "
			+ "(n.status = pe.sinapsistencia.notifications.domain.NotificationOutboxStatus.PENDING "
			+ "AND (n.nextAttemptAt IS NULL OR n.nextAttemptAt <= :now)) "
			+ "OR (n.status = pe.sinapsistencia.notifications.domain.NotificationOutboxStatus.PROCESSING "
			+ "AND n.lockedUntil < :now) "
			+ "ORDER BY n.createdAt ASC")
	List<UUID> findClaimableIds(@Param("now") Instant now, Pageable pageable);

	/**
	 * H-06: reclamo atómico vía UPDATE condicional -- repite la misma condición de
	 * {@link #findClaimableIds} en el WHERE para que dos workers no reclamen la
	 * misma fila (el segundo UPDATE no afecta filas, devuelve 0).
	 */
	@Modifying
	@Query("UPDATE NotificationOutbox n SET "
			+ "n.status = pe.sinapsistencia.notifications.domain.NotificationOutboxStatus.PROCESSING, "
			+ "n.workerToken = :token, n.lockedUntil = :lockedUntil "
			+ "WHERE n.id = :id AND ("
			+ "(n.status = pe.sinapsistencia.notifications.domain.NotificationOutboxStatus.PENDING "
			+ "AND (n.nextAttemptAt IS NULL OR n.nextAttemptAt <= :now)) "
			+ "OR (n.status = pe.sinapsistencia.notifications.domain.NotificationOutboxStatus.PROCESSING "
			+ "AND n.lockedUntil < :now))")
	int claim(@Param("id") UUID id, @Param("token") UUID token, @Param("lockedUntil") Instant lockedUntil,
			@Param("now") Instant now);
}
