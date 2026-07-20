package ogu1208.notificationdeliverysystem.domain.outbox;

import static java.util.Objects.requireNonNull;
import static org.springframework.util.Assert.state;

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
	@Index(name = "idx_outbox_event_status_updated_at", columnList = "status, updatedAt"),
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

	public void startPublishing() {
		state(status == OutboxEventStatus.PENDING, "대기 중인 Outbox 이벤트만 발행을 시작할 수 있습니다");

		status = OutboxEventStatus.PROCESSING;
	}

	public void markPublished() {
		state(status == OutboxEventStatus.PROCESSING, "처리 중인 Outbox 이벤트만 발행 완료할 수 있습니다");

		status = OutboxEventStatus.PUBLISHED;
		publishedAt = LocalDateTime.now();
	}

	public void markPending() {
		state(status == OutboxEventStatus.PROCESSING, "처리 중인 Outbox 이벤트만 대기 상태로 되돌릴 수 있습니다");

		status = OutboxEventStatus.PENDING;
	}
}
