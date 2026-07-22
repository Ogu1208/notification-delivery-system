package ogu1208.notificationdeliverysystem.application.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationSender;
import ogu1208.notificationdeliverysystem.application.notification.required.DispatchLock;
import ogu1208.notificationdeliverysystem.application.notification.required.DispatchLockManager;
import ogu1208.notificationdeliverysystem.application.notification.required.ExternalNotificationSender;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService implements NotificationSender {

	private final NotificationRepository notificationRepository;
	private final ExternalNotificationSender externalNotificationSender;
	private final DispatchLockManager dispatchLockManager;

	@Transactional
	@Override
	public void send(Long notificationId) {
		Notification notification = getNotification(notificationId);

		if (notification.isCompleted()) {
			log.info("완료 상태 알림 발송 건너뜀. notificationId={}, status={}", notification.getId(), notification.getStatus());
			return;
		}

		var lock = dispatchLockManager.tryAcquire(notificationId);
		if (lock.isEmpty()) {
			log.info("다른 Consumer가 처리 중인 알림 발송 건너뜀. notificationId={}", notificationId);
			return;
		}

		try (DispatchLock ignored = lock.get()) {
			dispatch(notification);
		}
	}

	private void dispatch(Notification notification) {
		notification.startProcessing();
		try {
			externalNotificationSender.send(notification);
		} catch (RuntimeException e) {
			notification.markFailed(e.getMessage());
			log.warn("알림 발송 실패. notificationId={}, reason={}", notification.getId(), e.getMessage());
			return;
		}

		notification.markSent();
	}

	private Notification getNotification(Long notificationId) {
		return notificationRepository.findById(notificationId)
			.orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다"));
	}
}
