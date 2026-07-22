package ogu1208.notificationdeliverysystem.application.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ogu1208.notificationdeliverysystem.application.notification.required.NotificationMessagePublisher;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

class NotificationRetryPublishServiceTest {

	private final FakeNotificationRepository notificationRepository = new FakeNotificationRepository();
	private final SpyNotificationMessagePublisher notificationMessagePublisher = new SpyNotificationMessagePublisher();
	private final NotificationRetryPublishService retryPublishService = new NotificationRetryPublishService(
		notificationRepository,
		notificationMessagePublisher
	);

	@Test
	@DisplayName("재시도 시간이 지난 알림을 다시 발행한다")
	void publishDueRetryWaitNotifications() {
		Notification notification = retryWaitNotification(1L, LocalDateTime.now().minusSeconds(1));
		notificationRepository.save(notification);

		int publishedCount = retryPublishService.publishDueRetries(10);

		assertThat(publishedCount).isEqualTo(1);
		assertThat(notificationRepository.lastFindLimit).isEqualTo(10);
		assertThat(notificationMessagePublisher.publishedNotifications).containsExactly(notification);
	}

	@Test
	@DisplayName("재시도 발행이 일부 실패해도 다음 알림 발행은 계속한다")
	void continuePublishingWhenOneRetryPublishFails() {
		Notification firstNotification = retryWaitNotification(1L, LocalDateTime.now().minusSeconds(1));
		Notification secondNotification = retryWaitNotification(2L, LocalDateTime.now().minusSeconds(1));
		notificationRepository.save(firstNotification);
		notificationRepository.save(secondNotification);
		notificationMessagePublisher.failedNotificationIds = Set.of(firstNotification.getId());

		int publishedCount = retryPublishService.publishDueRetries(10);

		assertThat(publishedCount).isEqualTo(1);
		assertThat(notificationMessagePublisher.publishAttemptCount).isEqualTo(2);
		assertThat(notificationMessagePublisher.publishedNotifications).containsExactly(secondNotification);
	}

	private Notification retryWaitNotification(Long id, LocalDateTime nextRetryAt) {
		Notification notification = NotificationFixture.createNotification(id);
		notification.startProcessing();
		notification.markRetryWait("mock api unavailable", "MOCK_UNAVAILABLE", 503, nextRetryAt);
		return notification;
	}

	private static class FakeNotificationRepository implements NotificationRepository {

		private final Map<Long, Notification> notifications = new LinkedHashMap<>();
		private int lastFindLimit;

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
			lastFindLimit = limit;
			return notifications.values()
				.stream()
				.filter(notification -> notification.getStatus() == NotificationStatus.RETRY_WAIT)
				.filter(notification -> !notification.getNextRetryAt().isAfter(now))
				.limit(limit)
				.toList();
		}
	}

	private static class SpyNotificationMessagePublisher implements NotificationMessagePublisher {

		private final List<Notification> publishedNotifications = new ArrayList<>();
		private Set<Long> failedNotificationIds = Set.of();
		private int publishAttemptCount;

		@Override
		public void publish(Notification notification) {
			publishAttemptCount++;
			if (failedNotificationIds.contains(notification.getId())) {
				throw new IllegalStateException("Kafka 발행 실패");
			}
			publishedNotifications.add(notification);
		}
	}
}
