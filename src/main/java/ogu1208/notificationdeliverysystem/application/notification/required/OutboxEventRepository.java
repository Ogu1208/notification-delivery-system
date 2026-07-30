package ogu1208.notificationdeliverysystem.application.notification.required;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import ogu1208.notificationdeliverysystem.domain.outbox.OutboxAggregateType;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;

public interface OutboxEventRepository {

	OutboxEvent save(OutboxEvent outboxEvent);

	Optional<OutboxEvent> findById(Long id);

	boolean markProcessingIfPending(Long id);

	List<OutboxEvent> findPendingOrderByIdAsc(int limit);

	List<OutboxEvent> findPendingCreatedBeforeOrderByIdAsc(LocalDateTime cutoff, int limit);

	List<OutboxEvent> findProcessingUpdatedBefore(LocalDateTime cutoff, int limit);

	Optional<OutboxEvent> findByAggregateTypeAndAggregateIdAndStatus(
		OutboxAggregateType aggregateType,
		Long aggregateId,
		OutboxEventStatus status
	);

	long countByStatus(OutboxEventStatus status);
}
