package ogu1208.notificationdeliverysystem.adapter.messaging.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ogu1208.notificationdeliverysystem.application.notification.NotificationOutboxPublishService;

@Component
@ConditionalOnProperty(name = "notification.outbox.publisher-enabled", havingValue = "true")
public class NotificationOutboxPoller {

	private final NotificationOutboxPublishService outboxPublishService;
	private final int batchSize;

	public NotificationOutboxPoller(
		NotificationOutboxPublishService outboxPublishService,
		@Value("${notification.outbox.polling-batch-size:100}") int batchSize
	) {
		this.outboxPublishService = outboxPublishService;
		this.batchSize = batchSize;
	}

	@Scheduled(fixedDelayString = "${notification.outbox.polling-interval-ms:1000}")
	public void publishPendingEvents() {
		outboxPublishService.publishPending(batchSize);
	}
}
