package ogu1208.notificationdeliverysystem.adapter.messaging.kafka;

import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationSender;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;

@ExtendWith(MockitoExtension.class)
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
}
