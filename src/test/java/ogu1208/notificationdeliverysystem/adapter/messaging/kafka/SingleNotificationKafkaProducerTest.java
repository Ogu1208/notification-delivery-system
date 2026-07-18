package ogu1208.notificationdeliverysystem.adapter.messaging.kafka;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

@ExtendWith(MockitoExtension.class)
class SingleNotificationKafkaProducerTest {

	@Mock
	private KafkaTemplate<String, SingleNotificationMessage> kafkaTemplate;

	@Test
	@DisplayName("단건 알림 발송 토픽에 알림 메시지를 발행한다")
	void publishSendsSingleNotificationMessageToKafkaTopic() {
		SingleNotificationKafkaProducer producer = new SingleNotificationKafkaProducer(
			kafkaTemplate,
			"single-notification-send"
		);
		Notification notification = NotificationFixture.createNotification(1L);
		given(kafkaTemplate.send(
			eq("single-notification-send"),
			eq("1"),
			argThat(message ->
				message.notificationId().equals(notification.getId())
					&& message.requestId().equals(notification.getRequestId().value())
					&& message.channel() == notification.getChannel()
					&& message.receiver().equals(notification.getReceiver())
			)
		)).willReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));

		producer.publish(notification);

		then(kafkaTemplate).should().send(
			eq("single-notification-send"),
			eq("1"),
			argThat(message ->
				message.notificationId().equals(notification.getId())
					&& message.requestId().equals(notification.getRequestId().value())
					&& message.channel() == notification.getChannel()
					&& message.receiver().equals(notification.getReceiver())
			)
		);
	}
}
