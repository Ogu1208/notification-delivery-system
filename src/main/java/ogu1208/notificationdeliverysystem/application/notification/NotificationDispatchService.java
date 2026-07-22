package ogu1208.notificationdeliverysystem.application.notification;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationSender;
import ogu1208.notificationdeliverysystem.application.notification.required.DispatchLock;
import ogu1208.notificationdeliverysystem.application.notification.required.DispatchLockManager;
import ogu1208.notificationdeliverysystem.application.notification.required.ExternalNotificationSendException;
import ogu1208.notificationdeliverysystem.application.notification.required.ExternalNotificationSender;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService implements NotificationSender {

	private static final int MAX_RETRY_COUNT = 3;
	private static final long RETRY_BACKOFF_SECONDS = 10;

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
		} catch (ExternalNotificationSendException e) {
			handleExternalSendFailure(notification, e);
			return;
		} catch (RuntimeException e) {
			notification.markFailed(e.getMessage());
			log.warn("알림 발송 실패. notificationId={}, reason={}", notification.getId(), e.getMessage());
			return;
		}

		notification.markSent();
	}

	private void handleExternalSendFailure(Notification notification, ExternalNotificationSendException e) {
		if (!e.isRetryable()) {
			notification.markFailed(e.getMessage());
			log.warn(
				"알림 발송 비재시도 실패. notificationId={}, requestId={}, errorCode={}, externalStatusCode={}, elapsedTimeMs={}, reason={}",
				notification.getId(),
				notification.getRequestId().value(),
				e.getErrorCode(),
				e.getExternalStatusCode(),
				e.getElapsedTimeMs(),
				e.getMessage()
			);
			return;
		}

		if (notification.getRetryCount() >= MAX_RETRY_COUNT) {
			notification.markDead("재시도 한도 초과: " + e.getMessage());
			log.warn(
				"알림 발송 재시도 한도 초과. notificationId={}, requestId={}, retryCount={}, errorCode={}, elapsedTimeMs={}, reason={}",
				notification.getId(),
				notification.getRequestId().value(),
				notification.getRetryCount(),
				e.getErrorCode(),
				e.getElapsedTimeMs(),
				e.getMessage()
			);
			return;
		}

		int nextRetryCount = notification.getRetryCount() + 1;
		LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(RETRY_BACKOFF_SECONDS * nextRetryCount);
		notification.markRetryWait(e.getMessage(), e.getErrorCode(), e.getExternalStatusCode(), nextRetryAt);
		log.warn(
			"알림 발송 재시도 대기. notificationId={}, requestId={}, retryCount={}, errorCode={}, externalStatusCode={}, elapsedTimeMs={}, nextRetryAt={}",
			notification.getId(),
			notification.getRequestId().value(),
			notification.getRetryCount(),
			e.getErrorCode(),
			e.getExternalStatusCode(),
			e.getElapsedTimeMs(),
			notification.getNextRetryAt()
		);
	}

	private Notification getNotification(Long notificationId) {
		return notificationRepository.findById(notificationId)
			.orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다"));
	}
}
