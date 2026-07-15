package ogu1208.notificationdeliverysystem.application.notification.provided;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;

public interface NotificationRegister {

	Notification register(NotificationRegisterRequest request);
}
