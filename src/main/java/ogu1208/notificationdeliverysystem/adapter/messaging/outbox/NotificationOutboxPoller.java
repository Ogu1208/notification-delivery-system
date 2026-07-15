package ogu1208.notificationdeliverysystem.adapter.messaging.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.NotificationOutboxPublishService;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.outbox.publisher-enabled", havingValue = "true")
public class NotificationOutboxPoller {

	private final NotificationOutboxPublishService outboxPublishService;

	@Scheduled(fixedDelayString = "${notification.outbox.polling-interval-ms:1000}")
	public void publishPendingEvents() {
		outboxPublishService.publishPending();
	}
}
