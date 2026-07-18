package ogu1208.notificationdeliverysystem.application.notification;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationMessagePublisher;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxAggregateType;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;

@Service
@Transactional
@RequiredArgsConstructor
public class NotificationOutboxPublishService {

	private static final int DEFAULT_BATCH_SIZE = 100;

	private final OutboxEventRepository outboxEventRepository;
	private final NotificationRepository notificationRepository;
	private final NotificationMessagePublisher notificationMessagePublisher;

	public int publishPending() {
		return publishPending(DEFAULT_BATCH_SIZE);
	}

	public int publishPending(int limit) {
		List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByIdAsc(
			OutboxEventStatus.PENDING,
			PageRequest.of(0, limit)
		);

		int publishedCount = 0;

		for (OutboxEvent outboxEvent : pendingEvents) {
			publish(outboxEvent);
			publishedCount++;
		}

		return publishedCount;
	}

	public boolean publishPendingByNotificationId(Long notificationId) {
		return outboxEventRepository.findByAggregateTypeAndAggregateIdAndStatus(
				OutboxAggregateType.NOTIFICATION,
				notificationId,
				OutboxEventStatus.PENDING
			)
			.map(outboxEvent -> {
				publish(outboxEvent);
				return true;
			})
			.orElse(false);
	}

	private void publish(OutboxEvent outboxEvent) {
		Notification notification = notificationRepository.findById(outboxEvent.getAggregateId())
			.orElseThrow(() -> new IllegalStateException("Outbox 이벤트의 알림을 찾을 수 없습니다"));

		notificationMessagePublisher.publish(notification);

		outboxEvent.markPublished();
		outboxEventRepository.save(outboxEvent);
	}
}
