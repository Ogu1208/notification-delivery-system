package ogu1208.notificationdeliverysystem.domain.notification;

public enum NotificationStatus {
	PENDING,
	SENDING,
	SENT,
	FAILED;

	public boolean canChangeTo(NotificationStatus targetStatus) {
		return switch (this) {
			case PENDING -> targetStatus == SENDING;
			case SENDING -> targetStatus == SENT || targetStatus == FAILED;
			case SENT, FAILED -> false;
		};
	}

	public boolean isCompleted() {
		return this == SENT || this == FAILED;
	}
}
