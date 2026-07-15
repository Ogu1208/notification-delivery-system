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
