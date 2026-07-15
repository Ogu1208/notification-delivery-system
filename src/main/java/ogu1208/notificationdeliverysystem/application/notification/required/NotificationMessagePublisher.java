package ogu1208.notificationdeliverysystem.application.notification.required;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;

public interface NotificationMessagePublisher {

	void publish(Notification notification);
}
