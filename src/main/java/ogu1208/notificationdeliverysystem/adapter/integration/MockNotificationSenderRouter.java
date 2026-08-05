package ogu1208.notificationdeliverysystem.adapter.integration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import ogu1208.notificationdeliverysystem.application.notification.required.ExternalNotificationSender;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;

@Component
@ConditionalOnProperty(name = "notification.role.worker-enabled", havingValue = "true")
public class MockNotificationSenderRouter implements ExternalNotificationSender {

	private final Map<NotificationChannel, MockChannelSender> senders;

	public MockNotificationSenderRouter(List<MockChannelSender> senders) {
		this.senders = new EnumMap<>(NotificationChannel.class);
		senders.forEach(sender -> this.senders.put(sender.channel(), sender));
	}

	@Override
	public void send(Notification notification) {
		MockChannelSender sender = senders.get(notification.getChannel());

		if (sender == null) {
			throw new IllegalArgumentException("지원하지 않는 알림 채널입니다");
		}

		sender.send(notification);
	}
}
