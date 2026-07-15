package ogu1208.notificationdeliverysystem.adapter.messaging.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationSender;

@Component
@ConditionalOnProperty(name = "notification.role.worker-enabled", havingValue = "true")
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

	private final NotificationSender notificationSender;

	// 기본 container factory와 group-id는 application.yml의 spring.kafka.consumer 설정을 사용
	@KafkaListener(topics = "${notification.kafka.topic}")
	public void consume(NotificationMessage message) {
		notificationSender.send(message.notificationId());
	}
}
