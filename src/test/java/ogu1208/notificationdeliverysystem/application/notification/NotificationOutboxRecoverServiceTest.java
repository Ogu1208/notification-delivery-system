package ogu1208.notificationdeliverysystem.application.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxAggregateType;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;

class NotificationOutboxRecoverServiceTest {

	private final FakeOutboxEventRepository outboxEventRepository = new FakeOutboxEventRepository();
	private final NotificationOutboxModifyService outboxModifyService = new NotificationOutboxModifyService(
		outboxEventRepository
	);
	private final NotificationOutboxRecoverService outboxRecoverService = new NotificationOutboxRecoverService(
		outboxModifyService
	);

	@Test
	@DisplayName("오래 처리 중인 Outbox 이벤트를 대기 상태로 되돌린다")
	void recoverStaleProcessingEvents() {
		OutboxEvent outboxEvent = OutboxEvent.singleNotificationSend(1L);
		outboxEventRepository.save(outboxEvent);
		outboxEvent.startPublishing();
		ReflectionTestUtils.setField(outboxEvent, "updatedAt", LocalDateTime.now().minusMinutes(10));

		int recoveredCount = outboxRecoverService.recoverStaleProcessingEvents(100);

		assertThat(recoveredCount).isOne();
		assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(outboxEventRepository.lastFindLimit).isEqualTo(100);
		assertThat(outboxEventRepository.savedStatuses).containsExactly(
			OutboxEventStatus.PENDING,
			OutboxEventStatus.PENDING
		);
	}

	private static class FakeOutboxEventRepository implements OutboxEventRepository {

		private final List<OutboxEvent> events = new ArrayList<>();
		private final List<OutboxEventStatus> savedStatuses = new ArrayList<>();
		private long idSequence = 1L;
		private int lastFindLimit;

		@Override
		public OutboxEvent save(OutboxEvent outboxEvent) {
			if (outboxEvent.getId() == null) {
				ReflectionTestUtils.setField(outboxEvent, "id", idSequence++);
			}
			if (!events.contains(outboxEvent)) {
				events.add(outboxEvent);
			}
			savedStatuses.add(outboxEvent.getStatus());
			return outboxEvent;
		}

		@Override
		public Optional<OutboxEvent> findById(Long id) {
			return events.stream()
				.filter(outboxEvent -> outboxEvent.getId().equals(id))
				.findFirst();
		}

		@Override
		public boolean markProcessingIfPending(Long id) {
			return false;
		}

		@Override
		public List<OutboxEvent> findPendingOrderByIdAsc(int limit) {
			return List.of();
		}

		@Override
		public List<OutboxEvent> findPendingCreatedBeforeOrderByIdAsc(LocalDateTime cutoff, int limit) {
			return List.of();
		}

		@Override
		public List<OutboxEvent> findProcessingUpdatedBefore(LocalDateTime cutoff, int limit) {
			lastFindLimit = limit;
			return events.stream()
				.filter(outboxEvent -> outboxEvent.getStatus() == OutboxEventStatus.PROCESSING)
				.filter(outboxEvent -> outboxEvent.getUpdatedAt().isBefore(cutoff))
				.limit(limit)
				.toList();
		}

		@Override
		public Optional<OutboxEvent> findByAggregateTypeAndAggregateIdAndStatus(
			OutboxAggregateType aggregateType,
			Long aggregateId,
			OutboxEventStatus status
		) {
			return Optional.empty();
		}

		@Override
		public long countByStatus(OutboxEventStatus status) {
			return events.stream()
				.filter(outboxEvent -> outboxEvent.getStatus() == status)
				.count();
		}
	}
}
