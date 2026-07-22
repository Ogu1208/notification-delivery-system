package ogu1208.notificationdeliverysystem.application.notification;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationMessagePublisher;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;

@Slf4j
@Service
public class NotificationOutboxPublishService {

	private static final int DEFAULT_BATCH_SIZE = 100;

	private final NotificationOutboxQueryService outboxQueryService;
	private final NotificationOutboxModifyService outboxModifyService;
	private final NotificationMessagePublisher notificationMessagePublisher;
	private final long pendingMinAgeMs;

	public NotificationOutboxPublishService(
		NotificationOutboxQueryService outboxQueryService,
		NotificationOutboxModifyService outboxModifyService,
		NotificationMessagePublisher notificationMessagePublisher,
		@Value("${notification.outbox.pending-min-age-ms:3000}") long pendingMinAgeMs
	) {
		this.outboxQueryService = outboxQueryService;
		this.outboxModifyService = outboxModifyService;
		this.notificationMessagePublisher = notificationMessagePublisher;
		this.pendingMinAgeMs = pendingMinAgeMs;
	}

	public int publishPending() {
		return publishPending(DEFAULT_BATCH_SIZE);
	}

	/**
	 * polling으로 남아 있는 PENDING Outbox 이벤트를 제한 개수만큼 발행
	 *
	 * <p>즉시 발행이 실패했거나 애플리케이션이 중간에 종료된 이벤트를 재처리하는 경로다.
	 * 특정 이벤트 발행이 실패해도 나머지 PENDING 이벤트 발행은 계속한다.</p>
	 */
	public int publishPending(int limit) {
		LocalDateTime cutoff = LocalDateTime.now().minus(Duration.ofMillis(pendingMinAgeMs));
		List<OutboxEvent> pendingEvents = outboxQueryService.findPendingEventsCreatedBefore(cutoff, limit);

		int publishedCount = 0;

		for (OutboxEvent outboxEvent : pendingEvents) {
			if (publishForPolling(outboxEvent)) {
				publishedCount++;
			}
		}

		return publishedCount;
	}

	private boolean publishForPolling(OutboxEvent outboxEvent) {
		try {
			publish(outboxEvent, "polling");
			return true;
		} catch (RuntimeException e) {
			log.warn("Outbox polling 발행 실패. 다음 이벤트 발행은 계속 진행합니다. outboxEventId={}", outboxEvent.getId(), e);
			return false;
		}
	}

	/**
	 * 방금 저장된 단건 알림 Outbox 이벤트를 commit 이후 즉시 발행
	 *
	 * <p>이 경로가 실패해도 이벤트는 PENDING으로 남고, polling 발행기가 다시 처리한다.</p>
	 */
	public boolean publishPendingByNotificationId(Long notificationId) {
		return outboxQueryService.findPendingEventByNotificationId(notificationId)
			.map(outboxEvent -> {
				publish(outboxEvent, "immediate");
				return true;
			})
			.orElse(false);
	}

	/*
	 * Kafka 발행 시도 전 Outbox를 PROCESSING으로 바꾸고, 성공을 확인한 뒤 PUBLISHED로 변경
	 * Kafka 발행은 DB 트랜잭션 밖 효과라 중복 발행 가능성이 있고, Consumer 멱등 처리로 보완한다.
	 */
	private void publish(OutboxEvent outboxEvent, String source) {
		if (!outboxModifyService.markProcessingIfPending(outboxEvent.getId())) {
			log.info(
				"Outbox Kafka 발행 건너뜀. source={}, outboxEventId={}, notificationId={}, reason=already-claimed",
				source,
				outboxEvent.getId(),
				outboxEvent.getAggregateId()
			);
			return;
		}

		try {
			Notification notification = outboxQueryService.findNotificationById(outboxEvent.getAggregateId());

			notificationMessagePublisher.publish(notification);

			outboxModifyService.markPublished(outboxEvent.getId());
			log.info(
				"Outbox Kafka 발행 성공. source={}, outboxEventId={}, notificationId={}, requestId={}",
				source,
				outboxEvent.getId(),
				notification.getId(),
				notification.getRequestId().value()
			);
		} catch (RuntimeException e) {
			outboxModifyService.markPending(outboxEvent.getId());
			throw e;
		}
	}
}
