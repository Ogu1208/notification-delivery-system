package ogu1208.notificationdeliverysystem.adapter.messaging.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationSender;

@Slf4j
@Component
@ConditionalOnProperty(name = "notification.role.worker-enabled", havingValue = "true")
@RequiredArgsConstructor
public class SingleNotificationKafkaConsumer {

	private final NotificationSender notificationSender;

	// 기본 container factory와 group-id는 application.yml의 spring.kafka.consumer 설정을 사용
	@KafkaListener(topics = "${notification.kafka.topics.single-notification-send}")
	public void consume(SingleNotificationMessage message) {
		log.info(
			"Kafka 알림 메시지 수신. notificationId={}, requestId={}, channel={}, receiver={}",
			message.notificationId(),
			message.requestId(),
			message.channel(),
			message.receiver()
		);
		notificationSender.send(message.notificationId());
	}
}
