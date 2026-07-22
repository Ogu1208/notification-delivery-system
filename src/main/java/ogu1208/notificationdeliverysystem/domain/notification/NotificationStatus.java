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
			case PROCESSING -> targetStatus == SENT || targetStatus == RETRY_WAIT || targetStatus == FAILED;
			case RETRY_WAIT -> targetStatus == PROCESSING || targetStatus == DEAD;
			case SENT, FAILED, DEAD -> false;
		};
	}

	public boolean isCompleted() {
		return this == SENT || this == FAILED || this == DEAD;
	}
}
