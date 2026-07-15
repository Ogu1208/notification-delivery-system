package ogu1208.notificationdeliverysystem.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ogu1208.notificationdeliverysystem.support.fixture.NotificationRegisterRequestFixture.create;

import org.junit.jupiter.api.Test;

import ogu1208.notificationdeliverysystem.support.fixture.NotificationRegisterRequestFixture;

class NotificationTest {

	@Test
	void registerCreatesPendingNotification() {
		Notification notification = Notification.register(create());

		assertThat(notification.getRequestId()).isEqualTo(new RequestId("req-001"));
		assertThat(notification.getReceiver()).isEqualTo("user@example.com");
		assertThat(notification.getChannel()).isEqualTo(NotificationChannel.EMAIL);
		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(notification.getAttemptCount()).isZero();
	}

	@Test
	void registerRejectsBlankRequiredValues() {
		assertThatThrownBy(() -> Notification.register(NotificationRegisterRequestFixture.builder().withRequestId(" ").build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("요청 ID는 필수입니다");
		assertThatThrownBy(() -> Notification.register(NotificationRegisterRequestFixture.builder().withReceiver(" ").build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("수신자는 필수입니다");
		assertThatThrownBy(() -> Notification.register(NotificationRegisterRequestFixture.builder().withChannel(null).build()))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("발송 채널은 필수입니다");
		assertThatThrownBy(() -> Notification.register(NotificationRegisterRequestFixture.builder().withTitle(" ").build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("제목은 필수입니다");
		assertThatThrownBy(() -> Notification.register(NotificationRegisterRequestFixture.builder().withContent(" ").build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("본문은 필수입니다");
	}

	@Test
	void notificationCanChangeFromPendingToSendingToSent() {
		Notification notification = Notification.register(create());

		notification.startSending();
		notification.markSent();

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(notification.getAttemptCount()).isEqualTo(1);
		assertThat(notification.getSendingStartedAt()).isNotNull();
		assertThat(notification.getSentAt()).isNotNull();
		assertThat(notification.isCompleted()).isTrue();
	}

	@Test
	void completedNotificationCannotStartSendingAgain() {
		Notification notification = Notification.register(create());
		notification.startSending();
		notification.markFailed("네트워크 오류");

		assertThatThrownBy(notification::startSending)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("최종 상태의 알림은 발송을 시작할 수 없습니다");
		assertThat(notification.getFailureReason()).isEqualTo("네트워크 오류");
		assertThat(notification.isCompleted()).isTrue();
	}

	@Test
	void notificationCannotBeMarkedSentBeforeSendingStarts() {
		Notification notification = Notification.register(create());

		assertThatThrownBy(notification::markSent)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("PENDING 상태에서 SENT 상태로 변경할 수 없습니다");
	}
}
