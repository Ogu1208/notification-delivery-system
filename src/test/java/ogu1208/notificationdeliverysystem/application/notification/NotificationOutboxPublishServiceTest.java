package ogu1208.notificationdeliverysystem.application.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import ogu1208.notificationdeliverysystem.application.notification.required.NotificationMessagePublisher;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

class NotificationOutboxPublishServiceTest {

	private final FakeNotificationRepository notificationRepository = new FakeNotificationRepository();
	private final FakeOutboxEventRepository outboxEventRepository = new FakeOutboxEventRepository();
	private final SpyNotificationMessagePublisher notificationMessagePublisher = new SpyNotificationMessagePublisher();
	private final NotificationOutboxPublishService outboxPublishService = new NotificationOutboxPublishService(
		outboxEventRepository,
		notificationRepository,
		notificationMessagePublisher
	);

	@Test
	@DisplayName("대기 중인 Outbox 이벤트의 알림 메시지를 발행하고 발행 완료로 변경한다")
	void publishPendingEventsAndMarksPublished() {
		Notification notification = NotificationFixture.createNotification(1L);
		notificationRepository.save(notification);
		OutboxEvent outboxEvent = OutboxEvent.singleNotificationSend(notification.getId());
		outboxEventRepository.save(outboxEvent);

		int publishedCount = outboxPublishService.publishPending();

		assertThat(publishedCount).isEqualTo(1);
		assertThat(outboxEventRepository.lastFindLimit).isEqualTo(100);
		assertThat(notificationMessagePublisher.publishedNotification).isSameAs(notification);
		assertThat(notificationMessagePublisher.publishCount).isEqualTo(1);
		assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
		assertThat(outboxEvent.getPublishedAt()).isNotNull();
		assertThat(outboxEventRepository.saveCount).isEqualTo(2);
	}

	@Test
	@DisplayName("대기 중인 Outbox 이벤트는 지정한 개수만 발행한다")
	void publishPendingEventsWithLimit() {
		Notification firstNotification = NotificationFixture.createNotification(1L);
		Notification secondNotification = NotificationFixture.createNotification(2L);
		notificationRepository.save(firstNotification);
		notificationRepository.save(secondNotification);
		outboxEventRepository.save(OutboxEvent.singleNotificationSend(firstNotification.getId()));
		outboxEventRepository.save(OutboxEvent.singleNotificationSend(secondNotification.getId()));

		int publishedCount = outboxPublishService.publishPending(1);

		assertThat(publishedCount).isEqualTo(1);
		assertThat(outboxEventRepository.lastFindLimit).isEqualTo(1);
		assertThat(notificationMessagePublisher.publishCount).isEqualTo(1);
	}

	@Test
	@DisplayName("알림 ID에 해당하는 대기 중 Outbox 이벤트를 즉시 발행한다")
	void publishPendingByNotificationId() {
		Notification notification = NotificationFixture.createNotification(1L);
		notificationRepository.save(notification);
		OutboxEvent outboxEvent = OutboxEvent.singleNotificationSend(notification.getId());
		outboxEventRepository.save(outboxEvent);

		boolean published = outboxPublishService.publishPendingByNotificationId(notification.getId());

		assertThat(published).isTrue();
		assertThat(notificationMessagePublisher.publishedNotification).isSameAs(notification);
		assertThat(notificationMessagePublisher.publishCount).isEqualTo(1);
		assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
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
		public Optional<Notification> findByRequestIdValue(String requestId) {
			return notifications.values()
				.stream()
				.filter(notification -> notification.getRequestId().value().equals(requestId))
				.findFirst();
		}
	}

	private static class FakeOutboxEventRepository implements OutboxEventRepository {

		private final List<OutboxEvent> events = new ArrayList<>();
		private int saveCount;
		private int lastFindLimit;

		@Override
		public OutboxEvent save(OutboxEvent outboxEvent) {
			if (!events.contains(outboxEvent)) {
				events.add(outboxEvent);
			}
			saveCount++;
			return outboxEvent;
		}

		@Override
		public List<OutboxEvent> findByStatusOrderByIdAsc(OutboxEventStatus status, Pageable pageable) {
			lastFindLimit = pageable.getPageSize();
			return events.stream()
				.filter(outboxEvent -> outboxEvent.getStatus() == status)
				.limit(pageable.getPageSize())
				.toList();
		}

		@Override
		public Optional<OutboxEvent> findByAggregateIdAndStatus(Long aggregateId, OutboxEventStatus status) {
			return events.stream()
				.filter(outboxEvent -> outboxEvent.getAggregateId().equals(aggregateId))
				.filter(outboxEvent -> outboxEvent.getStatus() == status)
				.findFirst();
		}
	}

	private static class SpyNotificationMessagePublisher implements NotificationMessagePublisher {

		private Notification publishedNotification;
		private int publishCount;

		@Override
		public void publish(Notification notification) {
			publishedNotification = notification;
			publishCount++;
		}
	}
}
