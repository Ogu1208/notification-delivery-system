package ogu1208.notificationdeliverysystem.adapter.messaging.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import ogu1208.notificationdeliverysystem.application.notification.required.NotificationMessagePublisher;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;

@Component
public class SingleNotificationKafkaProducer implements NotificationMessagePublisher {

	private final KafkaTemplate<String, SingleNotificationMessage> kafkaTemplate;
	private final String topic;

	public SingleNotificationKafkaProducer(
		KafkaTemplate<String, SingleNotificationMessage> kafkaTemplate,
		@Value("${notification.kafka.topics.single-notification-send}") String topic
	) {
		this.kafkaTemplate = kafkaTemplate;
		this.topic = topic;
	}

	@Override
	public void publish(Notification notification) {
		// KafkaTemplate은 Spring Boot가 application.yml의 spring.kafka 설정으로 자동 구성한다.
		kafkaTemplate.send(
			topic,
			String.valueOf(notification.getId()),
			SingleNotificationMessage.from(notification)
		).join();
	}
}
