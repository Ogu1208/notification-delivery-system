package ogu1208.notificationdeliverysystem.adapter.messaging.kafka;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;

public record NotificationMessage(
	Long notificationId,
	String requestId,
	NotificationChannel channel,
	String receiver
) {

	public static NotificationMessage from(Notification notification) {
		return new NotificationMessage(
			notification.getId(),
			notification.getRequestId().value(),
			notification.getChannel(),
			notification.getReceiver()
		);
	}
}
