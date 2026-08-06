package ogu1208.notificationdeliverysystem.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

@ExtendWith(MockitoExtension.class)
class NotificationAdminServiceTest {

	@Mock
	private NotificationRepository notificationRepository;

	@InjectMocks
	private NotificationAdminService notificationAdminService;

	@Test
	@DisplayName("DEAD 알림을 수동 재시도하면 RETRY_WAIT로 전이되고 nextRetryAt이 현재 시각으로 설정된다")
	void resurrectDeadNotificationForRetry() {
		Notification dead = deadNotification(42L);
		LocalDateTime beforeResurrect = LocalDateTime.now();
		when(notificationRepository.findById(42L)).thenReturn(Optional.of(dead));
		when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		Notification result = notificationAdminService.resurrectForRetry(42L);

		assertThat(result.getStatus()).isEqualTo(NotificationStatus.RETRY_WAIT);
		assertThat(result.getNextRetryAt()).isAfterOrEqualTo(beforeResurrect);
	}

	@Test
	@DisplayName("DEAD가 아닌 알림을 수동 재시도하면 IllegalStateException을 던진다")
	void rejectsNonDeadNotification() {
		Notification pending = Notification.register(NotificationFixture.builder().build());
		when(notificationRepository.findById(1L)).thenReturn(Optional.of(pending));

		assertThatThrownBy(() -> notificationAdminService.resurrectForRetry(1L))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("DEAD 상태의 알림만 수동 재시도할 수 있습니다");
	}

	@Test
	@DisplayName("존재하지 않는 알림 수동 재시도는 NotificationNotFoundException을 던진다")
	void rejectsMissingNotification() {
		when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> notificationAdminService.resurrectForRetry(999L))
			.isInstanceOf(NotificationNotFoundException.class)
			.hasMessageContaining("notificationId=999");
	}

	private Notification deadNotification(Long id) {
		Notification notification = Notification.register(NotificationFixture.builder()
			.withRequestId("req-" + id)
			.build());
		notification.startProcessing();
		notification.markRetryWait("mock down", "MOCK_UNAVAILABLE", 503, LocalDateTime.now().plusSeconds(10));
		notification.markDead("재시도 한도 초과");
		return notification;
	}
}
