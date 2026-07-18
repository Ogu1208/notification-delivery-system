package ogu1208.notificationdeliverysystem.domain.outbox;

import static java.util.Objects.requireNonNull;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ogu1208.notificationdeliverysystem.domain.common.BaseEntity;

@Getter
@Entity
@Table(indexes = {
	@Index(name = "idx_outbox_event_status_id", columnList = "status, id"),
	@Index(name = "idx_outbox_event_aggregate_status", columnList = "aggregateType, aggregateId, status")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {

	@Enumerated(EnumType.STRING)
	private OutboxAggregateType aggregateType;

	private Long aggregateId;

	@Enumerated(EnumType.STRING)
	private OutboxEventType eventType;

	@Enumerated(EnumType.STRING)
	private OutboxEventStatus status;

	private LocalDateTime publishedAt;

	private OutboxEvent(OutboxAggregateType aggregateType, Long aggregateId, OutboxEventType eventType) {
		this.aggregateType = requireNonNull(aggregateType, "집계 타입은 필수입니다");
		this.aggregateId = requireNonNull(aggregateId, "집계 ID는 필수입니다");
		this.eventType = requireNonNull(eventType, "이벤트 타입은 필수입니다");
		this.status = OutboxEventStatus.PENDING;
	}

	public static OutboxEvent singleNotificationSend(Long notificationId) {
		return new OutboxEvent(
			OutboxAggregateType.NOTIFICATION,
			notificationId,
			OutboxEventType.SINGLE_NOTIFICATION_SEND
		);
	}

	public void markPublished() {
		status = OutboxEventStatus.PUBLISHED;
		publishedAt = LocalDateTime.now();
	}
}
