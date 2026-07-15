package ogu1208.notificationdeliverysystem.adapter.messaging.outbox;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventNotifier;

@Component
@RequiredArgsConstructor
public class SpringOutboxEventNotifier implements OutboxEventNotifier {

	private final ApplicationEventPublisher applicationEventPublisher;

	@Override
	public void notifySaved(Long notificationId) {
		applicationEventPublisher.publishEvent(new NotificationOutboxSavedEvent(notificationId));
	}
}
