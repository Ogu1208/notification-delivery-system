package ogu1208.notificationdeliverysystem.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import ogu1208.notificationdeliverysystem.application.notification.required.NotificationMessagePublisher;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxAggregateType;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

@ExtendWith(OutputCaptureExtension.class)
class NotificationOutboxPublishServiceTest {

	private final FakeNotificationRepository notificationRepository = new FakeNotificationRepository();
	private final FakeOutboxEventRepository outboxEventRepository = new FakeOutboxEventRepository();
	private final SpyNotificationMessagePublisher notificationMessagePublisher = new SpyNotificationMessagePublisher();
	private final NotificationOutboxQueryService outboxQueryService = new NotificationOutboxQueryService(
		outboxEventRepository,
		notificationRepository
	);
	private final NotificationOutboxModifyService outboxModifyService = new NotificationOutboxModifyService(
		outboxEventRepository
	);
	private final NotificationOutboxPublishService outboxPublishService = new NotificationOutboxPublishService(
		outboxQueryService,
		outboxModifyService,
		notificationMessagePublisher,
		3000
	);

	@Test
	@DisplayName("대기 중인 Outbox 이벤트의 알림 메시지를 발행하고 발행 완료로 변경한다")
	void publishPendingEventsAndMarksPublished(CapturedOutput output) {
		Notification notification = NotificationFixture.createNotification(1L);
		notificationRepository.save(notification);
		OutboxEvent outboxEvent = OutboxEvent.singleNotificationSend(notification.getId());
		outboxEventRepository.save(outboxEvent);
		ReflectionTestUtils.setField(outboxEvent, "createdAt", LocalDateTime.now().minusSeconds(5));

		int publishedCount = outboxPublishService.publishPending();

		assertThat(publishedCount).isEqualTo(1);
		assertThat(outboxEventRepository.lastFindLimit).isEqualTo(100);
		assertThat(notificationMessagePublisher.publishedNotification).isSameAs(notification);
		assertThat(notificationMessagePublisher.publishCount).isEqualTo(1);
		assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
		assertThat(outboxEvent.getPublishedAt()).isNotNull();
		assertThat(outboxEventRepository.saveCount).isEqualTo(3);
		assertThat(outboxEventRepository.savedStatuses).containsExactly(
			OutboxEventStatus.PENDING,
			OutboxEventStatus.PROCESSING,
			OutboxEventStatus.PUBLISHED
		);
		assertThat(output).contains(
			"Outbox Kafka 발행 성공",
			"source=polling",
			"outboxEventId=" + outboxEvent.getId(),
			"notificationId=1",
			"requestId=" + notification.getRequestId().value()
		);
	}

	@Test
	@DisplayName("Outbox 발행 실패 시 처리 중 이벤트를 다시 대기 상태로 되돌린다")
	void returnsProcessingEventToPendingWhenPublishFails() {
		Notification notification = NotificationFixture.createNotification(1L);
		notificationRepository.save(notification);
		OutboxEvent outboxEvent = OutboxEvent.singleNotificationSend(notification.getId());
		outboxEventRepository.save(outboxEvent);
		notificationMessagePublisher.fail = true;

		assertThatThrownBy(() -> outboxPublishService.publishPendingByNotificationId(notification.getId()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Kafka 발행 실패");

		assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(outboxEvent.getPublishedAt()).isNull();
		assertThat(outboxEventRepository.savedStatuses).containsExactly(
			OutboxEventStatus.PENDING,
			OutboxEventStatus.PROCESSING,
			OutboxEventStatus.PENDING
		);
	}

	@Test
	@DisplayName("대기 중인 Outbox 이벤트는 지정한 개수만 발행한다")
	void publishPendingEventsWithLimit() {
		Notification firstNotification = NotificationFixture.createNotification(1L);
		Notification secondNotification = NotificationFixture.createNotification(2L);
		notificationRepository.save(firstNotification);
		notificationRepository.save(secondNotification);
		OutboxEvent firstOutboxEvent = outboxEventRepository.save(OutboxEvent.singleNotificationSend(firstNotification.getId()));
		OutboxEvent secondOutboxEvent = outboxEventRepository.save(OutboxEvent.singleNotificationSend(secondNotification.getId()));
		ReflectionTestUtils.setField(firstOutboxEvent, "createdAt", LocalDateTime.now().minusSeconds(5));
		ReflectionTestUtils.setField(secondOutboxEvent, "createdAt", LocalDateTime.now().minusSeconds(5));

		int publishedCount = outboxPublishService.publishPending(1);

		assertThat(publishedCount).isEqualTo(1);
		assertThat(outboxEventRepository.lastFindLimit).isEqualTo(1);
		assertThat(notificationMessagePublisher.publishCount).isEqualTo(1);
	}

	@Test
	@DisplayName("대기 중 이벤트 하나의 발행이 실패해도 다음 이벤트 발행은 계속한다")
	void continuesPublishingNextPendingEventWhenOnePublishFails() {
		Notification firstNotification = NotificationFixture.createNotification(1L);
		Notification secondNotification = NotificationFixture.createNotification(2L);
		notificationRepository.save(firstNotification);
		notificationRepository.save(secondNotification);
		OutboxEvent firstOutboxEvent = OutboxEvent.singleNotificationSend(firstNotification.getId());
		OutboxEvent secondOutboxEvent = OutboxEvent.singleNotificationSend(secondNotification.getId());
		outboxEventRepository.save(firstOutboxEvent);
		outboxEventRepository.save(secondOutboxEvent);
		ReflectionTestUtils.setField(firstOutboxEvent, "createdAt", LocalDateTime.now().minusSeconds(5));
		ReflectionTestUtils.setField(secondOutboxEvent, "createdAt", LocalDateTime.now().minusSeconds(5));
		notificationMessagePublisher.failedNotificationIds = Set.of(firstNotification.getId());

		int publishedCount = outboxPublishService.publishPending(2);

		assertThat(publishedCount).isEqualTo(1);
		assertThat(firstOutboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(secondOutboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
		assertThat(notificationMessagePublisher.publishAttemptCount).isEqualTo(2);
		assertThat(notificationMessagePublisher.publishCount).isEqualTo(1);
	}

	@Test
	@DisplayName("발행 poller는 처리 중 이벤트를 직접 복구하지 않는다")
	void publishPendingDoesNotRecoverProcessingEvents() {
		Notification notification = NotificationFixture.createNotification(1L);
		notificationRepository.save(notification);
		OutboxEvent outboxEvent = OutboxEvent.singleNotificationSend(notification.getId());
		outboxEventRepository.save(outboxEvent);
		outboxEvent.startPublishing();
		ReflectionTestUtils.setField(outboxEvent, "updatedAt", LocalDateTime.now().minusMinutes(10));

		int publishedCount = outboxPublishService.publishPending(1);

		assertThat(publishedCount).isZero();
		assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
		assertThat(notificationMessagePublisher.publishCount).isZero();
		assertThat(outboxEventRepository.savedStatuses).containsExactly(
			OutboxEventStatus.PENDING
		);
	}

	@Test
	@DisplayName("발행 poller는 방금 생성된 Outbox 이벤트를 즉시 발행하지 않는다")
	void publishPendingSkipsFreshPendingEvents() {
		Notification notification = NotificationFixture.createNotification(1L);
		notificationRepository.save(notification);
		OutboxEvent outboxEvent = OutboxEvent.singleNotificationSend(notification.getId());
		outboxEventRepository.save(outboxEvent);
		ReflectionTestUtils.setField(outboxEvent, "createdAt", LocalDateTime.now());

		int publishedCount = outboxPublishService.publishPending(1);

		assertThat(publishedCount).isZero();
		assertThat(notificationMessagePublisher.publishCount).isZero();
		assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
	}

	@Test
	@DisplayName("알림 ID에 해당하는 대기 중 Outbox 이벤트를 즉시 발행한다")
	void publishPendingByNotificationId(CapturedOutput output) {
		Notification notification = NotificationFixture.createNotification(1L);
		notificationRepository.save(notification);
		OutboxEvent outboxEvent = OutboxEvent.singleNotificationSend(notification.getId());
		outboxEventRepository.save(outboxEvent);

		boolean published = outboxPublishService.publishPendingByNotificationId(notification.getId());

		assertThat(published).isTrue();
		assertThat(notificationMessagePublisher.publishedNotification).isSameAs(notification);
		assertThat(notificationMessagePublisher.publishCount).isEqualTo(1);
		assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
		assertThat(output).contains(
			"Outbox Kafka 발행 성공",
			"source=immediate",
			"outboxEventId=" + outboxEvent.getId(),
			"notificationId=1",
			"requestId=" + notification.getRequestId().value()
		);
	}

	@Test
	@DisplayName("알림 ID에 해당하는 대기 중 Outbox 이벤트가 없으면 발행하지 않는다")
	void publishPendingByNotificationIdReturnsFalseWhenPendingEventDoesNotExist() {
		boolean published = outboxPublishService.publishPendingByNotificationId(1L);

		assertThat(published).isFalse();
		assertThat(notificationMessagePublisher.publishCount).isZero();
	}

	private static class FakeNotificationRepository implements NotificationRepository {

		private final Map<Long, Notification> notifications = new LinkedHashMap<>();

		@Override
		public Notification save(Notification notification) {
			notifications.put(notification.getId(), notification);
			return notification;
		}

		@Override
		public Optional<Notification> findById(Long id) {
			return Optional.ofNullable(notifications.get(id));
		}

		@Override
		public Optional<Notification> findByRequestId(String requestId) {
			return notifications.values()
				.stream()
				.filter(notification -> notification.getRequestId().value().equals(requestId))
				.findFirst();
		}

		@Override
		public List<Notification> findRetryWaitDue(LocalDateTime now, int limit) {
			return List.of();
		}
	}

	private static class FakeOutboxEventRepository implements OutboxEventRepository {

		private final List<OutboxEvent> events = new ArrayList<>();
		private final List<OutboxEventStatus> savedStatuses = new ArrayList<>();
		private long idSequence = 1L;
		private int saveCount;
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
			saveCount++;
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
			OutboxEvent outboxEvent = findById(id).orElseThrow();
			if (outboxEvent.getStatus() != OutboxEventStatus.PENDING) {
				return false;
			}
			outboxEvent.startPublishing();
			save(outboxEvent);
			return true;
		}

		@Override
		public List<OutboxEvent> findPendingOrderByIdAsc(int limit) {
			lastFindLimit = limit;
			return events.stream()
				.filter(outboxEvent -> outboxEvent.getStatus() == OutboxEventStatus.PENDING)
				.limit(limit)
				.toList();
		}

		@Override
		public List<OutboxEvent> findPendingCreatedBeforeOrderByIdAsc(LocalDateTime cutoff, int limit) {
			lastFindLimit = limit;
			return events.stream()
				.filter(outboxEvent -> outboxEvent.getStatus() == OutboxEventStatus.PENDING)
				.filter(outboxEvent -> outboxEvent.getCreatedAt().isBefore(cutoff))
				.limit(limit)
				.toList();
		}

		@Override
		public List<OutboxEvent> findProcessingUpdatedBefore(LocalDateTime cutoff, int limit) {
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
			return events.stream()
				.filter(outboxEvent -> outboxEvent.getAggregateType() == aggregateType)
				.filter(outboxEvent -> outboxEvent.getAggregateId().equals(aggregateId))
				.filter(outboxEvent -> outboxEvent.getStatus() == status)
				.findFirst();
		}

		@Override
		public long countByStatus(OutboxEventStatus status) {
			return events.stream()
				.filter(outboxEvent -> outboxEvent.getStatus() == status)
				.count();
		}
	}

	private static class SpyNotificationMessagePublisher implements NotificationMessagePublisher {

		private Notification publishedNotification;
		private int publishAttemptCount;
		private int publishCount;
		private boolean fail;
		private Set<Long> failedNotificationIds = Set.of();

		@Override
		public void publish(Notification notification) {
			publishAttemptCount++;
			if (fail || failedNotificationIds.contains(notification.getId())) {
				throw new IllegalStateException("Kafka 발행 실패");
			}
			publishedNotification = notification;
			publishCount++;
		}
	}
}
