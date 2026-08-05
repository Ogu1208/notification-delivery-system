package ogu1208.notificationdeliverysystem.adapter.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;

@Component
@ConditionalOnProperty(name = "notification.role.worker-enabled", havingValue = "true")
public class SmsMockSender extends AbstractMockChannelSender {

	public SmsMockSender(MockSendApiClient mockSendApiClient) {
		super(mockSendApiClient);
	}

	@Override
	public NotificationChannel channel() {
		return NotificationChannel.SMS;
	}
}
