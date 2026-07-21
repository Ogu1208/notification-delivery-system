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

	/**
	 * 즉시 발행에서 처리되지 않은 PENDING Outbox 이벤트를 주기적으로 재발행
	 *
	 * <p>fixedDelay는 이전 polling 실행이 끝난 뒤부터 다음 실행까지의 대기 시간이다.
	 * 따라서 batch 처리가 오래 걸리면 그 처리가 끝난 후 polling-interval-ms만큼 기다렸다가 다시 실행한다.</p>
	 */
	@Scheduled(fixedDelayString = "${notification.outbox.polling-interval-ms:1000}")
	public void publishPendingEvents() {
		outboxPublishService.publishPending(batchSize);
	}
}
