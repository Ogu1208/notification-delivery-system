package ogu1208.notificationdeliverysystem.adapter.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;

public abstract class AbstractMockChannelSender implements MockChannelSender {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Override
	public void send(Notification notification) {
		log.info(
			"목 발송 완료. notificationId={}, requestId={}, channel={}, receiver={}",
			notification.getId(),
			notification.getRequestId().value(),
			channel(),
			notification.getReceiver()
		);
	}

	@Override
	public abstract NotificationChannel channel();
}
