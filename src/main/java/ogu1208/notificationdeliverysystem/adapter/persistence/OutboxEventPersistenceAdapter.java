package ogu1208.notificationdeliverysystem.adapter.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxAggregateType;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;

@Component
@RequiredArgsConstructor
public class OutboxEventPersistenceAdapter implements OutboxEventRepository {

	private final OutboxEventJpaRepository outboxEventJpaRepository;

	@Override
	public OutboxEvent save(OutboxEvent outboxEvent) {
		return outboxEventJpaRepository.save(outboxEvent);
	}

	@Override
	public Optional<OutboxEvent> findById(Long id) {
		return outboxEventJpaRepository.findById(id);
	}

	@Override
	public boolean markProcessingIfPending(Long id) {
		return outboxEventJpaRepository.markProcessingIfPending(
			id,
			OutboxEventStatus.PENDING,
			OutboxEventStatus.PROCESSING
		) == 1;
	}

	@Override
	public List<OutboxEvent> findPendingOrderByIdAsc(int limit) {
		return outboxEventJpaRepository.findByStatusOrderByIdAsc(
			OutboxEventStatus.PENDING,
			PageRequest.of(0, limit)
		);
	}

	@Override
	public List<OutboxEvent> findPendingCreatedBeforeOrderByIdAsc(LocalDateTime cutoff, int limit) {
		return outboxEventJpaRepository.findByStatusAndCreatedAtBeforeOrderByIdAsc(
			OutboxEventStatus.PENDING,
			cutoff,
			PageRequest.of(0, limit)
		);
	}

	@Override
	public List<OutboxEvent> findProcessingUpdatedBefore(LocalDateTime cutoff, int limit) {
		return outboxEventJpaRepository.findByStatusAndUpdatedAtBeforeOrderByIdAsc(
			OutboxEventStatus.PROCESSING,
			cutoff,
			PageRequest.of(0, limit)
		);
	}

	@Override
	public Optional<OutboxEvent> findByAggregateTypeAndAggregateIdAndStatus(
		OutboxAggregateType aggregateType,
		Long aggregateId,
		OutboxEventStatus status
	) {
		return outboxEventJpaRepository.findByAggregateTypeAndAggregateIdAndStatus(aggregateType, aggregateId, status);
	}

	@Override
	public long countByStatus(OutboxEventStatus status) {
		return outboxEventJpaRepository.countByStatus(status);
	}
}
