package ogu1208.notificationdeliverysystem.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NotificationTest {

	@Test
	void registerCreatesPendingNotification() {
		Notification notification = Notification.register(
			"req-001",
			"user@example.com",
			NotificationChannel.EMAIL,
			"welcome",
			"hello"
		);

		assertThat(notification.getRequestId()).isEqualTo("req-001");
		assertThat(notification.getReceiver()).isEqualTo("user@example.com");
		assertThat(notification.getChannel()).isEqualTo(NotificationChannel.EMAIL);
		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(notification.getAttemptCount()).isZero();
	}

	@Test
	void registerRejectsBlankRequiredValues() {
		assertThatThrownBy(() -> Notification.register(" ", "user@example.com", NotificationChannel.EMAIL, "title", "content"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("요청 ID는 필수입니다");
		assertThatThrownBy(() -> Notification.register("req-001", " ", NotificationChannel.EMAIL, "title", "content"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("수신자는 필수입니다");
		assertThatThrownBy(() -> Notification.register("req-001", "user@example.com", null, "title", "content"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("발송 채널은 필수입니다");
		assertThatThrownBy(() -> Notification.register("req-001", "user@example.com", NotificationChannel.EMAIL, " ", "content"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("제목은 필수입니다");
		assertThatThrownBy(() -> Notification.register("req-001", "user@example.com", NotificationChannel.EMAIL, "title", " "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("본문은 필수입니다");
	}

	@Test
	void notificationCanMoveFromPendingToSendingToSent() {
		Notification notification = Notification.register(
			"req-001",
			"user@example.com",
			NotificationChannel.EMAIL,
			"welcome",
			"hello"
		);

		notification.startSending();
		notification.markSent();

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(notification.getAttemptCount()).isEqualTo(1);
		assertThat(notification.getSendingStartedAt()).isNotNull();
		assertThat(notification.getSentAt()).isNotNull();
		assertThat(notification.isTerminal()).isTrue();
	}

	@Test
	void terminalNotificationCannotStartSendingAgain() {
		Notification notification = Notification.register(
			"req-001",
			"user@example.com",
			NotificationChannel.EMAIL,
			"welcome",
			"hello"
		);
		notification.startSending();
		notification.markFailed("네트워크 오류");

		assertThatThrownBy(notification::startSending)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("최종 상태의 알림은 발송을 시작할 수 없습니다");
		assertThat(notification.getFailureReason()).isEqualTo("네트워크 오류");
		assertThat(notification.isTerminal()).isTrue();
	}
}
