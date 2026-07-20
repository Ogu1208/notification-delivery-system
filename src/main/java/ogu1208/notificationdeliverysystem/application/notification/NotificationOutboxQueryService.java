package ogu1208.notificationdeliverysystem.application.notification;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxAggregateType;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationOutboxQueryService {

	private final OutboxEventRepository outboxEventRepository;
	private final NotificationRepository notificationRepository;

	public List<OutboxEvent> findPendingEvents(int limit) {
		return outboxEventRepository.findByStatusOrderByIdAsc(
			OutboxEventStatus.PENDING,
			PageRequest.of(0, limit)
		);
	}

	public Optional<OutboxEvent> findPendingEventByNotificationId(Long notificationId) {
		return outboxEventRepository.findByAggregateTypeAndAggregateIdAndStatus(
			OutboxAggregateType.NOTIFICATION,
			notificationId,
			OutboxEventStatus.PENDING
		);
	}

	public Notification findNotificationById(Long notificationId) {
		return notificationRepository.findById(notificationId)
			.orElseThrow(() -> new IllegalStateException("Outbox 이벤트의 알림을 찾을 수 없습니다"));
	}
}
