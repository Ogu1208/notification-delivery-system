package ogu1208.notificationdeliverysystem.adapter.webapi.dto;

import java.time.LocalDateTime;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;

public record NotificationSearchItem(
	Long id,
	String requestId,
	String requesterId,
	String receiver,
	NotificationChannel channel,
	NotificationStatus status,
	String title,
	LocalDateTime createdAt
) {

	public static NotificationSearchItem from(Notification notification) {
		return new NotificationSearchItem(
			notification.getId(),
			notification.getRequestId().value(),
			notification.getRequesterId(),
			notification.getReceiver(),
			notification.getChannel(),
			notification.getStatus(),
			notification.getTitle(),
			notification.getCreatedAt()
		);
	}
}
