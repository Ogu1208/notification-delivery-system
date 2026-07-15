package ogu1208.notificationdeliverysystem.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture.createRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

class NotificationTest {

	@Test
	@DisplayName("알림 등록 요청으로 대기 상태 알림을 생성한다")
	void registerCreatesPendingNotification() {
		Notification notification = Notification.register(createRequest());

		assertThat(notification.getRequestId()).isEqualTo(new RequestId("req-001"));
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
	@DisplayName("대기 상태 알림은 발송 중을 거쳐 발송 완료 상태가 된다")
	void notificationCanChangeFromPendingToSendingToSent() {
		Notification notification = Notification.register(createRequest());

		notification.startSending();
		notification.markSent();

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(notification.getAttemptCount()).isEqualTo(1);
		assertThat(notification.getSendingStartedAt()).isNotNull();
		assertThat(notification.getSentAt()).isNotNull();
		assertThat(notification.isCompleted()).isTrue();
	}

	@Test
	@DisplayName("완료 상태 알림은 다시 발송을 시작할 수 없다")
	void completedNotificationCannotStartSendingAgain() {
		Notification notification = Notification.register(createRequest());
		notification.startSending();
		notification.markFailed("네트워크 오류");

		assertThatThrownBy(notification::startSending)
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
}
