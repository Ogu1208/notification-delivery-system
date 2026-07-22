package ogu1208.notificationdeliverysystem.adapter.messaging.retry;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ogu1208.notificationdeliverysystem.application.notification.NotificationRetryPublishService;

class NotificationRetryPollerTest {

	private final NotificationRetryPublishService retryPublishService = mock(NotificationRetryPublishService.class);
	private final NotificationRetryPoller retryPoller = new NotificationRetryPoller(retryPublishService, 50);

	@Test
	@DisplayName("재시도 polling 시 재시도 대상 알림 발행을 위임한다")
	void publishDueRetryNotifications() {
		retryPoller.publishDueRetries();

		verify(retryPublishService).publishDueRetries(50);
	}
}
