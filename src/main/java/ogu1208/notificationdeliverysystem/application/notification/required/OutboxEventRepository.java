package ogu1208.notificationdeliverysystem.application.notification.required;

import java.util.List;
import java.util.Optional;

import ogu1208.notificationdeliverysystem.domain.outbox.OutboxAggregateType;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;

public interface OutboxEventRepository {

	OutboxEvent save(OutboxEvent outboxEvent);

	Optional<OutboxEvent> findById(Long id);

	List<OutboxEvent> findPendingOrderByIdAsc(int limit);

	Optional<OutboxEvent> findByAggregateTypeAndAggregateIdAndStatus(
		OutboxAggregateType aggregateType,
		Long aggregateId,
		OutboxEventStatus status
	);
}
