package ogu1208.notificationdeliverysystem.adapter.messaging.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.NotificationOutboxPublishService;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.outbox.publisher-enabled", havingValue = "true")
public class NotificationOutboxEventListener {

	private static final Logger log = LoggerFactory.getLogger(NotificationOutboxEventListener.class);

	private final NotificationOutboxPublishService outboxPublishService;

	/**
	 * 트랜잭션 commit 직후 해당 Outbox 이벤트를 Kafka로 발행한다.
	 *
	 * <p>{@link OutboxAsyncConfig} Executor에서 비동기로 실행되며,
	 * 발행에 실패한 이벤트는 polling으로 복구된다.
	 */
	@Async(OutboxAsyncConfig.EXECUTOR_BEAN_NAME)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publishAfterCommit(NotificationOutboxSavedEvent event) {
		try {
			outboxPublishService.publishPendingByNotificationId(event.notificationId());
		} catch (RuntimeException e) {
			log.warn(
				"Outbox 즉시 발행 실패. polling 재시도 예정. notificationId={}, reason={}",
				event.notificationId(),
				e.getMessage()
			);
		}
	}
}
