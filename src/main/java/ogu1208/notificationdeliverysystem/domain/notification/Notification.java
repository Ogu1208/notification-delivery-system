package ogu1208.notificationdeliverysystem.domain.notification;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ogu1208.notificationdeliverysystem.domain.common.BaseEntity;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	name = "notification",
	uniqueConstraints = @UniqueConstraint(name = "uk_notification_request_id", columnNames = "requestId")
)
public class Notification extends BaseEntity {

	private String requestId;

	private String receiver;

	@Enumerated(EnumType.STRING)
	private NotificationChannel channel;

	private String title;

	private String content;

	@Enumerated(EnumType.STRING)
	private NotificationStatus status;

	private LocalDateTime sendingStartedAt;

	private LocalDateTime sentAt;

	private LocalDateTime failedAt;

	private String failureReason;

	private int attemptCount;

	private Notification(
		String requestId,
		String receiver,
		NotificationChannel channel,
		String title,
		String content
	) {
		this.requestId = new RequestId(requestId).value();
		this.receiver = requireText(receiver, "receiver");
		this.channel = requireNonNull(channel, "channel");
		this.title = requireText(title, "title");
		this.content = requireText(content, "content");
		this.status = NotificationStatus.PENDING;
		this.attemptCount = 0;
	}

	public static Notification register(
		String requestId,
		String receiver,
		NotificationChannel channel,
		String title,
		String content
	) {
		return new Notification(requestId, receiver, channel, title, content);
	}

	public void startSending() {
		if (isTerminal()) {
			throw new IllegalStateException("최종 상태의 알림은 발송을 시작할 수 없습니다");
		}
		if (status != NotificationStatus.PENDING) {
			throw new IllegalStateException("발송 대기 상태의 알림만 발송을 시작할 수 있습니다");
		}
		status = NotificationStatus.SENDING;
		sendingStartedAt = LocalDateTime.now();
		attemptCount++;
	}

	public void markSent() {
		if (status != NotificationStatus.SENDING) {
			throw new IllegalStateException("발송 중 상태의 알림만 발송 완료로 변경할 수 있습니다");
		}
		status = NotificationStatus.SENT;
		sentAt = LocalDateTime.now();
	}

	public void markFailed(String failureReason) {
		if (status != NotificationStatus.SENDING) {
			throw new IllegalStateException("발송 중 상태의 알림만 발송 실패로 변경할 수 있습니다");
		}
		status = NotificationStatus.FAILED;
		failedAt = LocalDateTime.now();
		this.failureReason = requireText(failureReason, "failureReason");
	}

	public boolean isTerminal() {
		return status == NotificationStatus.SENT || status == NotificationStatus.FAILED;
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(requiredMessage(fieldName));
		}
		return value;
	}

	private static <T> T requireNonNull(T value, String fieldName) {
		if (value == null) {
			throw new IllegalArgumentException(requiredMessage(fieldName));
		}
		return value;
	}

	private static String requiredMessage(String fieldName) {
		return switch (fieldName) {
			case "receiver" -> "수신자는 필수입니다";
			case "channel" -> "발송 채널은 필수입니다";
			case "title" -> "제목은 필수입니다";
			case "content" -> "본문은 필수입니다";
			case "failureReason" -> "실패 사유는 필수입니다";
			default -> fieldName + " 값은 필수입니다";
		};
	}
}
