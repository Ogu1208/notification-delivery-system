package ogu1208.notificationdeliverysystem.application.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationSender;
import ogu1208.notificationdeliverysystem.application.notification.required.ExternalNotificationSender;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventNotifier;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxAggregateType;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

class NotificationSenderTest {

	private final FakeNotificationRepository repository = new FakeNotificationRepository();
	private final SpyExternalNotificationSender externalSender = new SpyExternalNotificationSender();
	private final StubOutboxEventRepository outboxEventRepository = new StubOutboxEventRepository();
	private final StubOutboxEventNotifier outboxEventNotifier = new StubOutboxEventNotifier();
	private final NotificationSender notificationSender = new NotificationModifyService(
		repository,
		externalSender,
		outboxEventRepository,
		outboxEventNotifier
	);

	@Test
	@DisplayName("대기 중인 알림을 발송하고 성공 상태로 변경한다")
	void sendPendingNotificationAndMarksSent() {
		Notification notification = NotificationFixture.createNotification(1L);
		repository.save(notification);

		notificationSender.send(1L);

		assertThat(externalSender.sentNotification).isSameAs(notification);
		assertThat(externalSender.sendCount).isEqualTo(1);
		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(notification.getAttemptCount()).isEqualTo(1);
		assertThat(notification.getSendingStartedAt()).isNotNull();
		assertThat(notification.getSentAt()).isNotNull();
	}

	@Test
	@DisplayName("이미 완료된 알림은 다시 발송하지 않는다")
	void sendSkipsCompletedNotification() {
		Notification notification = NotificationFixture.createNotification(1L);
		notification.startSending();
		notification.markSent();
		repository.save(notification);

		notificationSender.send(1L);

		assertThat(externalSender.sendCount).isZero();
		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
	}

	@Test
	@DisplayName("외부 발송이 실패하면 실패 상태로 변경한다")
	void sendMarksFailedWhenExternalSenderFails() {
		Notification notification = NotificationFixture.createNotification(1L);
		repository.save(notification);
		externalSender.failure = new RuntimeException("mock failure");

		notificationSender.send(1L);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
		assertThat(notification.getAttemptCount()).isEqualTo(1);
		assertThat(notification.getFailureReason()).isEqualTo("mock failure");
		assertThat(notification.getFailedAt()).isNotNull();
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
	}

	private static class StubOutboxEventRepository implements OutboxEventRepository {

		@Override
		public OutboxEvent save(OutboxEvent outboxEvent) {
			return outboxEvent;
		}

		@Override
		public List<OutboxEvent> findByStatusOrderByIdAsc(OutboxEventStatus status, Pageable pageable) {
			return List.of();
		}

		@Override
		public Optional<OutboxEvent> findByAggregateTypeAndAggregateIdAndStatus(
			OutboxAggregateType aggregateType,
			Long aggregateId,
			OutboxEventStatus status
		) {
			return Optional.empty();
		}
	}

	private static class StubOutboxEventNotifier implements OutboxEventNotifier {

		@Override
		public void notifyOutboxSaved(Long notificationId) {
		}
	}

	private static class SpyExternalNotificationSender implements ExternalNotificationSender {

		private Notification sentNotification;
		private RuntimeException failure;
		private int sendCount;

		@Override
		public void send(Notification notification) {
			sentNotification = notification;
			sendCount++;
			if (failure != null) {
				throw failure;
			}
		}
	}
}
