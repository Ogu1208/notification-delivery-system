package ogu1208.notificationdeliverysystem.adapter.messaging.kafka;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;

public record SingleNotificationMessage(
	Long notificationId,
	String requestId,
	NotificationChannel channel,
	String receiver
) {

	public static SingleNotificationMessage from(Notification notification) {
		return new SingleNotificationMessage(
			notification.getId(),
			notification.getRequestId().value(),
			notification.getChannel(),
			notification.getReceiver()
		);
	}
}
