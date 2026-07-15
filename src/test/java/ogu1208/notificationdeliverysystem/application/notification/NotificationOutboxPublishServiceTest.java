package ogu1208.notificationdeliverysystem.application.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
		assertThat(notificationMessagePublisher.publishedNotification).isSameAs(notification);
		assertThat(notificationMessagePublisher.publishCount).isEqualTo(1);
		assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
		assertThat(outboxEvent.getPublishedAt()).isNotNull();
		assertThat(outboxEventRepository.saveCount).isEqualTo(2);
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

		@Override
		public OutboxEvent save(OutboxEvent outboxEvent) {
			if (!events.contains(outboxEvent)) {
				events.add(outboxEvent);
			}
			saveCount++;
			return outboxEvent;
		}

		@Override
		public List<OutboxEvent> findByStatus(OutboxEventStatus status) {
			return events.stream()
				.filter(outboxEvent -> outboxEvent.getStatus() == status)
				.toList();
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
