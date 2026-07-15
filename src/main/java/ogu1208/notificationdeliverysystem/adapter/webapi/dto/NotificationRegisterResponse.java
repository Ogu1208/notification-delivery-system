package ogu1208.notificationdeliverysystem.adapter.webapi.dto;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;

public record NotificationRegisterResponse(
	Long notificationId,
	NotificationStatus status,
	String requestId
) {

	public static NotificationRegisterResponse from(Notification notification) {
		return new NotificationRegisterResponse(
			notification.getId(),
			notification.getStatus(),
			notification.getRequestId()
		);
	}
}
