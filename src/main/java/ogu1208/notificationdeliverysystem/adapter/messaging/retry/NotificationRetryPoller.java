package ogu1208.notificationdeliverysystem.adapter.messaging.retry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ogu1208.notificationdeliverysystem.application.notification.NotificationRetryPublishService;

@Component
@ConditionalOnProperty(name = "notification.retry.publisher-enabled", havingValue = "true")
public class NotificationRetryPoller {

	private final NotificationRetryPublishService retryPublishService;
	private final int batchSize;

	public NotificationRetryPoller(
		NotificationRetryPublishService retryPublishService,
		@Value("${notification.retry.polling-batch-size:100}") int batchSize
	) {
		this.retryPublishService = retryPublishService;
		this.batchSize = batchSize;
	}

	@Scheduled(fixedDelayString = "${notification.retry.polling-interval-ms:1000}")
	public void publishDueRetries() {
		retryPublishService.publishDueRetries(batchSize);
	}
}
