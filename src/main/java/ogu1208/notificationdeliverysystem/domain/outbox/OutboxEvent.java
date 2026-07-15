package ogu1208.notificationdeliverysystem.domain.outbox;

import static java.util.Objects.requireNonNull;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ogu1208.notificationdeliverysystem.domain.common.BaseEntity;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {

	private Long aggregateId;

	@Enumerated(EnumType.STRING)
	private OutboxEventType eventType;

	@Enumerated(EnumType.STRING)
	private OutboxEventStatus status;

	private LocalDateTime publishedAt;

	private OutboxEvent(Long aggregateId, OutboxEventType eventType) {
		this.aggregateId = requireNonNull(aggregateId, "집계 ID는 필수입니다");
		this.eventType = requireNonNull(eventType, "이벤트 타입은 필수입니다");
		this.status = OutboxEventStatus.PENDING;
	}

	public static OutboxEvent singleNotificationSend(Long notificationId) {
		return new OutboxEvent(notificationId, OutboxEventType.SINGLE_NOTIFICATION_SEND);
	}

	public void markPublished() {
		status = OutboxEventStatus.PUBLISHED;
		publishedAt = LocalDateTime.now();
	}
}
