package ogu1208.notificationdeliverysystem.adapter.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationSender;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SingleNotificationKafkaConsumerTest {

	@Mock
	private NotificationSender notificationSender;

	@Test
	@DisplayName("Kafka 메시지를 받으면 알림 발송 유스케이스를 호출한다")
	void consumeMessageAndSendNotification() {
		SingleNotificationKafkaConsumer consumer = new SingleNotificationKafkaConsumer(notificationSender);
		SingleNotificationMessage message = new SingleNotificationMessage(
			1L,
			"req-001",
			NotificationChannel.EMAIL,
			"user@example.com"
		);

		consumer.consume(message);

		then(notificationSender).should().send(1L);
	}

	@Test
	@DisplayName("Kafka 메시지 수신 성공 로그에 알림 식별자를 남긴다")
	void consumeLogsKafkaMessage(CapturedOutput output) {
		SingleNotificationKafkaConsumer consumer = new SingleNotificationKafkaConsumer(notificationSender);
		SingleNotificationMessage message = new SingleNotificationMessage(
			1L,
			"req-001",
			NotificationChannel.EMAIL,
			"user@example.com"
		);

		consumer.consume(message);

		assertThat(output).contains(
			"Kafka 알림 메시지 수신",
			"notificationId=1",
			"requestId=req-001"
		);
	}
}
