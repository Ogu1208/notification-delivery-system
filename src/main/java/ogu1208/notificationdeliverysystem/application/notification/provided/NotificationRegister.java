package ogu1208.notificationdeliverysystem.application.notification.provided;

import jakarta.validation.Valid;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;

public interface NotificationRegister {

	Notification register(@Valid NotificationRegisterRequest request);
}
