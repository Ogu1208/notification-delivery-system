package ogu1208.notificationdeliverysystem.adapter.messaging.outbox;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ogu1208.notificationdeliverysystem.application.notification.NotificationOutboxPublishService;

class NotificationOutboxEventListenerTest {

	private final NotificationOutboxPublishService outboxPublishService = mock(NotificationOutboxPublishService.class);
	private final NotificationOutboxEventListener eventListener = new NotificationOutboxEventListener(outboxPublishService);

	@Test
	@DisplayName("Outbox 저장 이벤트를 받으면 즉시 발행을 시도한다")
	void publishAfterCommitPublishesPendingOutboxEvent() {
		eventListener.publishAfterCommit(new NotificationOutboxSavedEvent(1L));

		verify(outboxPublishService).publishPendingByNotificationId(1L);
	}

	@Test
	@DisplayName("즉시 발행 실패는 polling 재시도를 위해 예외를 전파하지 않는다")
	void publishAfterCommitDoesNotPropagateFailure() {
		doThrow(new RuntimeException("kafka down"))
			.when(outboxPublishService)
			.publishPendingByNotificationId(1L);

		eventListener.publishAfterCommit(new NotificationOutboxSavedEvent(1L));

		verify(outboxPublishService).publishPendingByNotificationId(1L);
	}
}
