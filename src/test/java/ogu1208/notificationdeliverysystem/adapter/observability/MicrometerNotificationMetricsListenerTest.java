package ogu1208.notificationdeliverysystem.adapter.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;
import ogu1208.notificationdeliverysystem.domain.notification.event.DispatchLockAttempted;
import ogu1208.notificationdeliverysystem.domain.notification.event.FinalStateSkipped;
import ogu1208.notificationdeliverysystem.domain.notification.event.NotificationStateTransitioned;

class MicrometerNotificationMetricsListenerTest {

	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
	private final MicrometerNotificationMetricsListener listener =
		new MicrometerNotificationMetricsListener(registry);

	@Test
	void 상태_전이_이벤트는_notification_status_transitions_카운터를_증가시킨다() {
		listener.onStateTransitioned(
			new NotificationStateTransitioned(1L, NotificationChannel.SMS, NotificationStatus.SENT)
		);

		double count = registry
			.counter("notification.status.transitions", "to", "SENT", "channel", "SMS")
			.count();
		assertThat(count).isEqualTo(1.0);
	}

	@Test
	void 락_획득_시도_이벤트는_notification_dispatch_lock_acquire_카운터를_증가시킨다() {
		listener.onDispatchLockAttempted(
			new DispatchLockAttempted(1L, DispatchLockAttempted.Result.MISSED)
		);

		double count = registry
			.counter("notification.dispatch.lock.acquire", "result", "MISSED")
			.count();
		assertThat(count).isEqualTo(1.0);
	}

	@Test
	void 최종_상태_skip_이벤트는_notification_final_state_skip_카운터를_증가시킨다() {
		listener.onFinalStateSkipped(
			new FinalStateSkipped(1L, NotificationStatus.SENT)
		);

		double count = registry
			.counter("notification.final.state.skip", "status", "SENT")
			.count();
		assertThat(count).isEqualTo(1.0);
	}
}
