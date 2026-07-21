package ogu1208.notificationdeliverysystem.adapter.messaging.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ogu1208.notificationdeliverysystem.application.notification.NotificationOutboxRecoverService;

@Component
@ConditionalOnProperty(name = "notification.outbox.publisher-enabled", havingValue = "true")
public class NotificationOutboxRecoveryPoller {

	private final NotificationOutboxRecoverService outboxRecoverService;
	private final int batchSize;

	public NotificationOutboxRecoveryPoller(
		NotificationOutboxRecoverService outboxRecoverService,
		@Value("${notification.outbox.recovery-batch-size:500}") int batchSize
	) {
		this.outboxRecoverService = outboxRecoverService;
		this.batchSize = batchSize;
	}

	/**
	 * 오래 PROCESSING 상태로 남은 Outbox 이벤트를 PENDING으로 복구
	 *
	 * <p>발행 polling과 별도로 실행해 복구 backlog가 커져도 일반 PENDING 발행을 막지 않는다.</p>
	 */
	@Scheduled(fixedDelayString = "${notification.outbox.recovery-interval-ms:30000}")
	public void recoverStaleProcessingEvents() {
		outboxRecoverService.recoverStaleProcessingEvents(batchSize);
	}
}
