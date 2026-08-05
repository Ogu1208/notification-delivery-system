package ogu1208.notificationdeliverysystem.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture.createRequest;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

class NotificationTest {

	@Test
	@DisplayName("알림 등록 요청으로 대기 상태 알림을 생성한다")
	void registerCreatesPendingNotification() {
		Notification notification = Notification.register(createRequest());

		assertThat(notification.getRequestId()).isEqualTo(new RequestId("req-001"));
		assertThat(notification.getRequesterId()).isEqualTo("requester-001");
		assertThat(notification.getReceiver()).isEqualTo("user@example.com");
		assertThat(notification.getChannel()).isEqualTo(NotificationChannel.EMAIL);
		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(notification.getAttemptCount()).isZero();
	}

	@Test
	@DisplayName("알림 등록 필수 값이 비어 있으면 생성을 거부한다")
	void registerRejectsBlankRequiredValues() {
		assertThatThrownBy(() -> Notification.register(NotificationFixture.builder().withRequestId(" ").build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("요청 ID는 필수입니다");
		assertThatThrownBy(() -> Notification.register(NotificationFixture.builder().withRequesterId(" ").build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("요청자는 필수입니다");
		assertThatThrownBy(() -> Notification.register(NotificationFixture.builder().withReceiver(" ").build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("수신자는 필수입니다");
		assertThatThrownBy(() -> Notification.register(NotificationFixture.builder().withChannel(null).build()))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("발송 채널은 필수입니다");
		assertThatThrownBy(() -> Notification.register(NotificationFixture.builder().withTitle(" ").build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("제목은 필수입니다");
		assertThatThrownBy(() -> Notification.register(NotificationFixture.builder().withContent(" ").build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("본문은 필수입니다");
	}

	@Test
	@DisplayName("대기 상태 알림은 처리 중을 거쳐 발송 완료 상태가 된다")
	void notificationCanChangeFromPendingToProcessingToSent() {
		Notification notification = Notification.register(createRequest());

		notification.startProcessing();
		notification.markSent();

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(notification.getAttemptCount()).isEqualTo(1);
		assertThat(notification.getSendingStartedAt()).isNotNull();
		assertThat(notification.getSentAt()).isNotNull();
		assertThat(notification.isCompleted()).isTrue();
	}

	@Test
	@DisplayName("완료 상태 알림은 다시 발송을 시작할 수 없다")
	void completedNotificationCannotStartProcessingAgain() {
		Notification notification = Notification.register(createRequest());
		notification.startProcessing();
		notification.markFailed("네트워크 오류");

		assertThatThrownBy(notification::startProcessing)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("최종 상태의 알림은 발송을 시작할 수 없습니다");
		assertThat(notification.getFailureReason()).isEqualTo("네트워크 오류");
		assertThat(notification.isCompleted()).isTrue();
	}

	@Test
	@DisplayName("발송 시작 전 알림은 발송 완료로 변경할 수 없다")
	void notificationCannotBeMarkedSentBeforeSendingStarts() {
		Notification notification = Notification.register(createRequest());

		assertThatThrownBy(notification::markSent)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("PENDING 상태에서 SENT 상태로 변경할 수 없습니다");
	}

	@Test
	@DisplayName("재시도 가능한 실패는 재시도 대기 상태와 실패 정보를 남긴다")
	void retryableFailureChangesNotificationToRetryWait() {
		Notification notification = Notification.register(createRequest());
		LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(5);

		notification.startProcessing();
		notification.markRetryWait("Mock API timeout", "MOCK_TIMEOUT", null, nextRetryAt);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.RETRY_WAIT);
		assertThat(notification.getRetryCount()).isEqualTo(1);
		assertThat(notification.getLastError()).isEqualTo("Mock API timeout");
		assertThat(notification.getLastErrorCode()).isEqualTo("MOCK_TIMEOUT");
		assertThat(notification.getLastExternalStatusCode()).isNull();
		assertThat(notification.getLastFailedAt()).isNotNull();
		assertThat(notification.getNextRetryAt()).isEqualTo(nextRetryAt);
		assertThat(notification.isCompleted()).isFalse();
	}

	@Test
	@DisplayName("재시도 대기 상태 알림은 다시 처리 중 상태가 될 수 있다")
	void retryWaitNotificationCanStartProcessingAgain() {
		Notification notification = Notification.register(createRequest());

		notification.startProcessing();
		notification.markRetryWait("Mock API unavailable", "MOCK_UNAVAILABLE", 503, LocalDateTime.now().plusSeconds(5));
		notification.startProcessing();

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
		assertThat(notification.getAttemptCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("재시도 한도를 초과한 알림은 데드 상태가 된다")
	void retryExhaustedNotificationChangesToDead() {
		Notification notification = Notification.register(createRequest());

		notification.startProcessing();
		notification.markRetryWait("Mock API unavailable", "MOCK_UNAVAILABLE", 503, LocalDateTime.now().plusSeconds(5));
		notification.markDead("재시도 한도 초과");

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.DEAD);
		assertThat(notification.getFailureReason()).isEqualTo("재시도 한도 초과");
		assertThat(notification.getFailedAt()).isNotNull();
		assertThat(notification.isCompleted()).isTrue();
	}
}
