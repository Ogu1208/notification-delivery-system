package ogu1208.notificationdeliverysystem.adapter.messaging.outbox;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ogu1208.notificationdeliverysystem.application.notification.NotificationOutboxPublishService;

class NotificationOutboxPollerTest {

	private final NotificationOutboxPublishService outboxPublishService = mock(NotificationOutboxPublishService.class);
	private final NotificationOutboxPoller outboxPoller = new NotificationOutboxPoller(outboxPublishService);

	@Test
	@DisplayName("Outbox polling 시 대기 중인 이벤트 발행을 위임한다")
	void publishPendingEvents() {
		outboxPoller.publishPendingEvents();

		verify(outboxPublishService).publishPending();
	}
}
