package ogu1208.notificationdeliverysystem.adapter.messaging.kafka;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import ogu1208.notificationdeliverysystem.application.notification.required.NotificationMessagePublisher;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;

@Component
public class SingleNotificationKafkaProducer implements NotificationMessagePublisher {

	private final KafkaTemplate<String, SingleNotificationMessage> kafkaTemplate;
	private final String topic;
	private final long publishTimeoutMs;

	public SingleNotificationKafkaProducer(
		KafkaTemplate<String, SingleNotificationMessage> kafkaTemplate,
		@Value("${notification.kafka.topics.single-notification-send}") String topic,
		@Value("${notification.kafka.publish-timeout-ms:3000}") long publishTimeoutMs
	) {
		this.kafkaTemplate = kafkaTemplate;
		this.topic = topic;
		this.publishTimeoutMs = publishTimeoutMs;
	}

	@Override
	public void publish(Notification notification) {
		// KafkaTemplate은 Spring Boot가 application.yml의 spring.kafka 설정으로 자동 구성한다.
		try {
			kafkaTemplate.send(
				topic,
				String.valueOf(notification.getId()),
				SingleNotificationMessage.from(notification)
			).get(publishTimeoutMs, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Kafka 알림 메시지 발행이 중단되었습니다", e);
		} catch (ExecutionException | TimeoutException e) {
			throw new IllegalStateException("Kafka 알림 메시지 발행에 실패했습니다", e);
		}
	}
}
