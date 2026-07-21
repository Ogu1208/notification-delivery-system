package ogu1208.notificationdeliverysystem.adapter.messaging.outbox;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ogu1208.notificationdeliverysystem.application.notification.NotificationOutboxRecoverService;

class NotificationOutboxRecoveryPollerTest {

	private final NotificationOutboxRecoverService outboxRecoverService = mock(NotificationOutboxRecoverService.class);
	private final NotificationOutboxRecoveryPoller outboxRecoveryPoller = new NotificationOutboxRecoveryPoller(
		outboxRecoverService,
		200
	);

	@Test
	@DisplayName("Outbox 복구 polling 시 오래 처리 중인 이벤트 복구를 위임한다")
	void recoverStaleProcessingEvents() {
		outboxRecoveryPoller.recoverStaleProcessingEvents();

		verify(outboxRecoverService).recoverStaleProcessingEvents(200);
	}
}
