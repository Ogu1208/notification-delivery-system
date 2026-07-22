package ogu1208.notificationdeliverysystem.adapter.integration;

import org.springframework.stereotype.Component;

import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;

@Component
public class SmsMockSender extends AbstractMockChannelSender {

	public SmsMockSender(MockSendApiClient mockSendApiClient) {
		super(mockSendApiClient);
	}

	@Override
	public NotificationChannel channel() {
		return NotificationChannel.SMS;
	}
}
