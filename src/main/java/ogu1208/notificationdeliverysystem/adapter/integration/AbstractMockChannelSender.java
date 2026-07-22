package ogu1208.notificationdeliverysystem.adapter.integration;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;

public abstract class AbstractMockChannelSender implements MockChannelSender {

	private final MockSendApiClient mockSendApiClient;

	protected AbstractMockChannelSender(MockSendApiClient mockSendApiClient) {
		this.mockSendApiClient = mockSendApiClient;
	}

	@Override
	public void send(Notification notification) {
		mockSendApiClient.send(notification, channel());
	}

	@Override
	public abstract NotificationChannel channel();
}
