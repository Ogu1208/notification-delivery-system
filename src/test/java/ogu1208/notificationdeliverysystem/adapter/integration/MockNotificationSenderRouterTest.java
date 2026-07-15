package ogu1208.notificationdeliverysystem.adapter.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

class MockNotificationSenderRouterTest {

	private final SpyMockChannelSender emailSender = new SpyMockChannelSender(NotificationChannel.EMAIL);
	private final SpyMockChannelSender smsSender = new SpyMockChannelSender(NotificationChannel.SMS);
	private final SpyMockChannelSender kakaoSender = new SpyMockChannelSender(NotificationChannel.KAKAO);
	private final MockNotificationSenderRouter router = new MockNotificationSenderRouter(
		List.of(emailSender, smsSender, kakaoSender)
	);

	@Test
	@DisplayName("EMAIL 알림은 EMAIL Mock Sender로 발송한다")
	void sendEmailNotificationToEmailSender() {
		Notification notification = createNotification(NotificationChannel.EMAIL);

		router.send(notification);

		assertThat(emailSender.sentNotification).isSameAs(notification);
		assertThat(smsSender.sendCount).isZero();
		assertThat(kakaoSender.sendCount).isZero();
	}

	@Test
	@DisplayName("SMS 알림은 SMS Mock Sender로 발송한다")
	void sendSmsNotificationToSmsSender() {
		Notification notification = createNotification(NotificationChannel.SMS);

		router.send(notification);

		assertThat(smsSender.sentNotification).isSameAs(notification);
		assertThat(emailSender.sendCount).isZero();
		assertThat(kakaoSender.sendCount).isZero();
	}

	@Test
	@DisplayName("KAKAO 알림은 KAKAO Mock Sender로 발송한다")
	void sendKakaoNotificationToKakaoSender() {
		Notification notification = createNotification(NotificationChannel.KAKAO);

		router.send(notification);

		assertThat(kakaoSender.sentNotification).isSameAs(notification);
		assertThat(emailSender.sendCount).isZero();
		assertThat(smsSender.sendCount).isZero();
	}

	private Notification createNotification(NotificationChannel channel) {
		Notification notification = Notification.register(NotificationFixture.builder()
			.withChannel(channel)
			.build());
		ReflectionTestUtils.setField(notification, "id", 1L);
		return notification;
	}

	private static class SpyMockChannelSender implements MockChannelSender {

		private final NotificationChannel channel;
		private Notification sentNotification;
		private int sendCount;

		private SpyMockChannelSender(NotificationChannel channel) {
			this.channel = channel;
		}

		@Override
		public NotificationChannel channel() {
			return channel;
		}

		@Override
		public void send(Notification notification) {
			sentNotification = notification;
			sendCount++;
		}
	}
}
