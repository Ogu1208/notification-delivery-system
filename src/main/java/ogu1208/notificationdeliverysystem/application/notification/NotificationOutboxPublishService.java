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

	/**
	 * polling으로 남아 있는 PENDING Outbox 이벤트를 제한 개수만큼 발행
	 *
	 * <p>즉시 발행이 실패했거나 애플리케이션이 중간에 종료된 이벤트를 재처리하는 경로다.</p>
	 */
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

	/**
	 * 방금 저장된 단건 알림 Outbox 이벤트를 commit 이후 즉시 발행
	 *
	 * <p>이 경로가 실패해도 이벤트는 PENDING으로 남고, polling 발행기가 다시 처리한다.</p>
	 */
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

	/*
	 * Kafka 발행 성공을 확인한 뒤 Outbox를 PUBLISHED로 변경
	 * Kafka 발행은 DB 트랜잭션 밖 효과라 중복 발행 가능성이 있고, Consumer 멱등 처리로 보완한다.
	 */
	private void publish(OutboxEvent outboxEvent) {
		Notification notification = notificationRepository.findById(outboxEvent.getAggregateId())
			.orElseThrow(() -> new IllegalStateException("Outbox 이벤트의 알림을 찾을 수 없습니다"));

		notificationMessagePublisher.publish(notification);

		outboxEvent.markPublished();
		outboxEventRepository.save(outboxEvent);
	}
}
