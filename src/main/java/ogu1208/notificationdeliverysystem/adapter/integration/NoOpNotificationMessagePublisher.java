package ogu1208.notificationdeliverysystem.adapter.integration;

import org.springframework.stereotype.Component;

import ogu1208.notificationdeliverysystem.application.notification.required.NotificationMessagePublisher;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;

@Component
public class NoOpNotificationMessagePublisher implements NotificationMessagePublisher {

	@Override
	public void publish(Notification notification) {
	}
}
