package ogu1208.notificationdeliverysystem.application.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationMessagePublisher;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;

@Service
@Transactional
@RequiredArgsConstructor
public class NotificationOutboxPublishService {

	private final OutboxEventRepository outboxEventRepository;
	private final NotificationRepository notificationRepository;
	private final NotificationMessagePublisher notificationMessagePublisher;

	public int publishPending() {
		int publishedCount = 0;
		for (OutboxEvent outboxEvent : outboxEventRepository.findByStatus(OutboxEventStatus.PENDING)) {
			Notification notification = notificationRepository.findById(outboxEvent.getAggregateId())
				.orElseThrow(() -> new IllegalStateException("Outbox 이벤트의 알림을 찾을 수 없습니다"));
			notificationMessagePublisher.publish(notification);
			outboxEvent.markPublished();
			outboxEventRepository.save(outboxEvent);
			publishedCount++;
		}
		return publishedCount;
	}
}
