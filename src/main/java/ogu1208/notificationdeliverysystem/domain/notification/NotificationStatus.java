package ogu1208.notificationdeliverysystem.domain.notification;

public enum NotificationStatus {
	PENDING,
	PROCESSING,
	SENT,
	RETRY_WAIT,
	FAILED,
	DEAD;

	public boolean canChangeTo(NotificationStatus targetStatus) {
		return switch (this) {
			case PENDING -> targetStatus == PROCESSING;
			case PROCESSING -> targetStatus == SENT || targetStatus == RETRY_WAIT || targetStatus == FAILED
				|| targetStatus == DEAD;
			case RETRY_WAIT -> targetStatus == PROCESSING || targetStatus == DEAD;
			// DEAD → RETRY_WAIT는 관리자 수동 재처리 전용 전이. 자동 흐름에서는 발생하지 않음
			// (isCompleted()가 true라 poller 등에서 DEAD를 픽업하지 않기 때문).
			case DEAD -> targetStatus == RETRY_WAIT;
			case SENT, FAILED -> false;
		};
	}

	public boolean isCompleted() {
		return this == SENT || this == FAILED || this == DEAD;
	}
}
