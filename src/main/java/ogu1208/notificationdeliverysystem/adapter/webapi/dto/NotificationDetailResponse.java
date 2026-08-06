package ogu1208.notificationdeliverysystem.adapter.webapi.dto;

import java.time.LocalDateTime;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;

/**
 * 알림 단건 상세 조회 응답.
 *
 * <p>목록 응답({@link NotificationSearchItem})과 달리 {@code content}와 처리 이력
 * (재시도, 실패 사유 등)까지 모두 포함한다.
 */
public record NotificationDetailResponse(
	Long id,
	String requestId,
	String requesterId,
	String receiver,
	NotificationChannel channel,
	NotificationStatus status,
	String title,
	String content,
	int attemptCount,
	int retryCount,
	String lastError,
	String lastErrorCode,
	Integer lastExternalStatusCode,
	String failureReason,
	LocalDateTime sendingStartedAt,
	LocalDateTime sentAt,
	LocalDateTime failedAt,
	LocalDateTime lastFailedAt,
	LocalDateTime nextRetryAt,
	LocalDateTime createdAt
) {

	public static NotificationDetailResponse from(Notification notification) {
		return new NotificationDetailResponse(
			notification.getId(),
			notification.getRequestId().value(),
			notification.getRequesterId(),
			notification.getReceiver(),
			notification.getChannel(),
			notification.getStatus(),
			notification.getTitle(),
			notification.getContent(),
			notification.getAttemptCount(),
			notification.getRetryCount(),
			notification.getLastError(),
			notification.getLastErrorCode(),
			notification.getLastExternalStatusCode(),
			notification.getFailureReason(),
			notification.getSendingStartedAt(),
			notification.getSentAt(),
			notification.getFailedAt(),
			notification.getLastFailedAt(),
			notification.getNextRetryAt(),
			notification.getCreatedAt()
		);
	}
}
