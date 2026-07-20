package ogu1208.notificationdeliverysystem.adapter.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import ogu1208.notificationdeliverysystem.domain.outbox.OutboxAggregateType;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;

interface OutboxEventJpaRepository extends Repository<OutboxEvent, Long> {

	OutboxEvent save(OutboxEvent outboxEvent);

	Optional<OutboxEvent> findById(Long id);

	List<OutboxEvent> findByStatusOrderByIdAsc(OutboxEventStatus status, Pageable pageable);

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
