package ogu1208.notificationdeliverysystem.application.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationSender;
import ogu1208.notificationdeliverysystem.application.notification.required.DispatchLock;
import ogu1208.notificationdeliverysystem.application.notification.required.DispatchLockManager;
import ogu1208.notificationdeliverysystem.application.notification.required.ExternalNotificationSendException;
import ogu1208.notificationdeliverysystem.application.notification.required.ExternalNotificationSender;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

@ExtendWith(OutputCaptureExtension.class)
class NotificationSenderTest {

	private final FakeNotificationRepository repository = new FakeNotificationRepository();
	private final SpyExternalNotificationSender externalSender = new SpyExternalNotificationSender();
	private final FakeDispatchLockManager dispatchLockManager = new FakeDispatchLockManager();
	private final NotificationRetryPolicy retryPolicy = new NotificationRetryPolicy(3, List.of(10L, 20L, 30L));
	private final NotificationSender notificationSender = new NotificationDispatchService(
		repository,
		externalSender,
		dispatchLockManager,
		retryPolicy
	);

	@Test
	@DisplayName("대기 중인 알림을 발송하고 성공 상태로 변경한다")
	void sendPendingNotificationAndMarksSent(CapturedOutput output) {
		Notification notification = NotificationFixture.createNotification(1L);
		repository.save(notification);

		notificationSender.send(1L);

		assertThat(externalSender.sentNotification).isSameAs(notification);
		assertThat(externalSender.sendCount).isEqualTo(1);
		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(notification.getAttemptCount()).isEqualTo(1);
		assertThat(notification.getSendingStartedAt()).isNotNull();
		assertThat(notification.getSentAt()).isNotNull();
		assertThat(dispatchLockManager.tryAcquireCount).isEqualTo(1);
		assertThat(dispatchLockManager.closedLockCount).isEqualTo(1);
		assertThat(output).contains(
			"알림 발송 성공",
			"notificationId=1",
			"requestId=" + notification.getRequestId().value(),
			"attemptCount=1"
		);
	}

	@Test
	@DisplayName("이미 완료된 알림은 다시 발송하지 않는다")
	void sendSkipsCompletedNotification() {
		Notification notification = NotificationFixture.createNotification(1L);
		notification.startProcessing();
		notification.markSent();
		repository.save(notification);

		notificationSender.send(1L);

		assertThat(externalSender.sendCount).isZero();
		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(dispatchLockManager.tryAcquireCount).isZero();
	}

	@Test
	@DisplayName("외부 발송이 실패하면 실패 상태로 변경한다")
	void sendMarksFailedWhenExternalSenderFails() {
		Notification notification = NotificationFixture.createNotification(1L);
		repository.save(notification);
		externalSender.failure = ExternalNotificationSendException.nonRetryable(
			"invalid receiver",
			"MOCK_BAD_REQUEST",
			400,
			null
		);

		notificationSender.send(1L);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
		assertThat(notification.getAttemptCount()).isEqualTo(1);
		assertThat(notification.getFailureReason()).isEqualTo("invalid receiver");
		assertThat(notification.getFailedAt()).isNotNull();
		assertThat(dispatchLockManager.closedLockCount).isEqualTo(1);
	}

	@Test
	@DisplayName("외부 발송이 일시 실패하면 재시도 대기 상태로 변경한다")
	void sendMarksRetryWaitWhenExternalSenderTemporarilyFails() {
		Notification notification = NotificationFixture.createNotification(1L);
		repository.save(notification);
		externalSender.failure = ExternalNotificationSendException.retryable(
			"mock api timeout",
			"MOCK_TIMEOUT",
			null,
			null
		);

		notificationSender.send(1L);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.RETRY_WAIT);
		assertThat(notification.getRetryCount()).isEqualTo(1);
		assertThat(notification.getLastError()).isEqualTo("mock api timeout");
		assertThat(notification.getLastErrorCode()).isEqualTo("MOCK_TIMEOUT");
		assertThat(notification.getNextRetryAt()).isNotNull();
	}

	@Test
	@DisplayName("설정된 backoff 초를 기준으로 다음 재시도 시간을 계산한다")
	void sendSchedulesNextRetryWithConfiguredBackoff() {
		NotificationSender notificationSender = new NotificationDispatchService(
			repository,
			externalSender,
			dispatchLockManager,
			new NotificationRetryPolicy(3, List.of(60L, 120L, 180L))
		);
		Notification notification = NotificationFixture.createNotification(1L);
		repository.save(notification);
		externalSender.failure = ExternalNotificationSendException.retryable(
			"mock api timeout",
			"MOCK_TIMEOUT",
			null,
			null
		);
		LocalDateTime beforeSend = LocalDateTime.now();

		notificationSender.send(1L);

		assertThat(notification.getNextRetryAt()).isAfterOrEqualTo(beforeSend.plusSeconds(60));
	}

	@Test
	@DisplayName("재시도 한도를 초과하면 데드 상태로 변경한다")
	void sendMarksDeadWhenRetryLimitExceeded() {
		Notification notification = NotificationFixture.createNotification(1L);
		notification.startProcessing();
		notification.markRetryWait("mock api unavailable", "MOCK_UNAVAILABLE", 503, LocalDateTime.now().minusSeconds(1));
		notification.startProcessing();
		notification.markRetryWait("mock api unavailable", "MOCK_UNAVAILABLE", 503, LocalDateTime.now().minusSeconds(1));
		notification.startProcessing();
		notification.markRetryWait("mock api unavailable", "MOCK_UNAVAILABLE", 503, LocalDateTime.now().minusSeconds(1));
		repository.save(notification);
		externalSender.failure = ExternalNotificationSendException.retryable(
			"mock api unavailable",
			"MOCK_UNAVAILABLE",
			503,
			null
		);

		notificationSender.send(1L);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.DEAD);
		assertThat(notification.getRetryCount()).isEqualTo(3);
		assertThat(notification.getFailureReason()).isEqualTo("재시도 한도 초과: mock api unavailable");
	}

	@Test
	@DisplayName("발송 락을 획득하지 못하면 외부 발송을 건너뛴다")
	void sendSkipsExternalSenderWhenDispatchLockIsNotAcquired() {
		Notification notification = NotificationFixture.createNotification(1L);
		repository.save(notification);
		dispatchLockManager.acquire = false;

		notificationSender.send(1L);

		assertThat(externalSender.sendCount).isZero();
		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(dispatchLockManager.tryAcquireCount).isEqualTo(1);
		assertThat(dispatchLockManager.closedLockCount).isZero();
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

	private static class FakeDispatchLockManager implements DispatchLockManager {

		private boolean acquire = true;
		private int tryAcquireCount;
		private int closedLockCount;

		@Override
		public Optional<DispatchLock> tryAcquire(Long notificationId) {
			tryAcquireCount++;

			if (!acquire) {
				return Optional.empty();
			}

			return Optional.of(() -> closedLockCount++);
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
