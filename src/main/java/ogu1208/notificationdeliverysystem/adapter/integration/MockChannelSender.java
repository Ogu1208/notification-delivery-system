package ogu1208.notificationdeliverysystem.adapter.integration;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;

public interface MockChannelSender {

	NotificationChannel channel();

	void send(Notification notification);
}
