package ogu1208.notificationdeliverysystem.application.notification.provided;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;

public interface NotificationRegister {

	Notification register(NotificationRegisterRequest request);
}
