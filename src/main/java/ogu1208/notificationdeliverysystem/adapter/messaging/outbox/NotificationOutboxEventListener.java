package ogu1208.notificationdeliverysystem.adapter.messaging.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
	 * 알림과 Outbox 저장 트랜잭션이 commit된 뒤 즉시 Kafka 발행 시도
	 *
	 * <p>실패해도 예외를 전파하지 않는다. Outbox polling 경로가 남은 PENDING 이벤트를 재시도한다.</p>
	 */
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
