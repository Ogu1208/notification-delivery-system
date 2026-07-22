package ogu1208.notificationdeliverysystem.adapter.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import ogu1208.notificationdeliverysystem.domain.outbox.OutboxAggregateType;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;

interface OutboxEventJpaRepository extends Repository<OutboxEvent, Long> {

	OutboxEvent save(OutboxEvent outboxEvent);

	Optional<OutboxEvent> findById(Long id);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update OutboxEvent e
		set e.status = :processingStatus
		where e.id = :id
			and e.status = :pendingStatus
		""")
	int markProcessingIfPending(
		@Param("id") Long id,
		@Param("pendingStatus") OutboxEventStatus pendingStatus,
		@Param("processingStatus") OutboxEventStatus processingStatus
	);

	List<OutboxEvent> findByStatusOrderByIdAsc(OutboxEventStatus status, Pageable pageable);

	List<OutboxEvent> findByStatusAndCreatedAtBeforeOrderByIdAsc(
		OutboxEventStatus status,
		LocalDateTime cutoff,
		Pageable pageable
	);

	List<OutboxEvent> findByStatusAndUpdatedAtBeforeOrderByIdAsc(
		OutboxEventStatus status,
		LocalDateTime cutoff,
		Pageable pageable
	);

	Optional<OutboxEvent> findByAggregateTypeAndAggregateIdAndStatus(
		OutboxAggregateType aggregateType,
		Long aggregateId,
		OutboxEventStatus status
	);
}
