package ogu1208.notificationdeliverysystem.domain.notification;

import static java.util.Objects.requireNonNull;
import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.state;

import java.time.LocalDateTime;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

	@Embedded
	@AttributeOverride(name = "value", column = @Column(name = "requestId", nullable = false))
	private RequestId requestId;

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

	private int retryCount;

	private String lastError;

	private String lastErrorCode;

	private Integer lastExternalStatusCode;

	private LocalDateTime lastFailedAt;

	private LocalDateTime nextRetryAt;

	private Notification(NotificationRegisterRequest request) {
		this.requestId = new RequestId(request.requestId());
		hasText(request.receiver(), "수신자는 필수입니다");
		this.receiver = request.receiver();
		this.channel = requireNonNull(request.channel(), "발송 채널은 필수입니다");
		hasText(request.title(), "제목은 필수입니다");
		this.title = request.title();
		hasText(request.content(), "본문은 필수입니다");
		this.content = request.content();
		this.status = NotificationStatus.PENDING;
		this.attemptCount = 0;
		this.retryCount = 0;
	}

	public static Notification register(NotificationRegisterRequest request) {
		return new Notification(request);
	}

	public void startProcessing() {
		state(!isCompleted(), "최종 상태의 알림은 발송을 시작할 수 없습니다");
		changeStatusTo(NotificationStatus.PROCESSING);
		sendingStartedAt = LocalDateTime.now();
		attemptCount++;
	}

	public void markSent() {
		changeStatusTo(NotificationStatus.SENT);
		sentAt = LocalDateTime.now();
	}

	public void markFailed(String failureReason) {
		changeStatusTo(NotificationStatus.FAILED);
		failedAt = LocalDateTime.now();
		hasText(failureReason, "실패 사유는 필수입니다");
		this.failureReason = failureReason;
	}

	public void markRetryWait(String lastError, String lastErrorCode, Integer lastExternalStatusCode,
		LocalDateTime nextRetryAt) {
		changeStatusTo(NotificationStatus.RETRY_WAIT);
		hasText(lastError, "실패 사유는 필수입니다");
		hasText(lastErrorCode, "실패 코드는 필수입니다");
		this.retryCount++;
		this.lastError = lastError;
		this.lastErrorCode = lastErrorCode;
		this.lastExternalStatusCode = lastExternalStatusCode;
		this.lastFailedAt = LocalDateTime.now();
		this.nextRetryAt = requireNonNull(nextRetryAt, "다음 재시도 시각은 필수입니다");
	}

	public void markDead(String failureReason) {
		changeStatusTo(NotificationStatus.DEAD);
		failedAt = LocalDateTime.now();
		hasText(failureReason, "실패 사유는 필수입니다");
		this.failureReason = failureReason;
	}

	public boolean isCompleted() {
		return status.isCompleted();
	}

	private void changeStatusTo(NotificationStatus targetStatus) {
		state(
			status.canChangeTo(targetStatus),
			status + " 상태에서 " + targetStatus + " 상태로 변경할 수 없습니다"
		);
		status = targetStatus;
	}
}
