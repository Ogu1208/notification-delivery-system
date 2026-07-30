package ogu1208.notificationdeliverysystem.application.notification;

import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationSender;
import ogu1208.notificationdeliverysystem.application.notification.required.DispatchLock;
import ogu1208.notificationdeliverysystem.application.notification.required.DispatchLockManager;
import ogu1208.notificationdeliverysystem.application.notification.required.ExternalNotificationSendException;
import ogu1208.notificationdeliverysystem.application.notification.required.ExternalNotificationSender;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;
import ogu1208.notificationdeliverysystem.domain.notification.event.DispatchLockAttempted;
import ogu1208.notificationdeliverysystem.domain.notification.event.FinalStateSkipped;
import ogu1208.notificationdeliverysystem.domain.notification.event.NotificationStateTransitioned;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService implements NotificationSender {

	private final NotificationRepository notificationRepository;
	private final ExternalNotificationSender externalNotificationSender;
	private final DispatchLockManager dispatchLockManager;
	private final NotificationRetryPolicy retryPolicy;
	private final ApplicationEventPublisher eventPublisher;

	@Timed(
		value = "notification.send",
		description = "알림 발송 유스케이스 소요 시간",
		percentiles = {0.5, 0.95, 0.99}
	)
	@Transactional
	@Override
	public void send(Long notificationId) {
		Notification notification = getNotification(notificationId);

		if (notification.isCompleted()) {
			log.info("완료 상태 알림 발송 건너뜀. notificationId={}, status={}", notification.getId(), notification.getStatus());
			eventPublisher.publishEvent(new FinalStateSkipped(notification.getId(), notification.getStatus()));
			return;
		}

		var lock = dispatchLockManager.tryAcquire(notificationId);
		if (lock.isEmpty()) {
			log.info("다른 Consumer가 처리 중인 알림 발송 건너뜀. notificationId={}", notificationId);
			eventPublisher.publishEvent(new DispatchLockAttempted(notificationId, DispatchLockAttempted.Result.MISSED));
			return;
		}
		eventPublisher.publishEvent(new DispatchLockAttempted(notificationId, DispatchLockAttempted.Result.ACQUIRED));

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
			publishTransitioned(notification, NotificationStatus.FAILED);
			log.warn(
				"알림 발송 예상치 못한 실패. notificationId={}, requestId={}, channel={}, attemptCount={}, exceptionType={}, reason={}",
				notification.getId(),
				notification.getRequestId().value(),
				notification.getChannel(),
				notification.getAttemptCount(),
				e.getClass().getSimpleName(),
				e.getMessage(),
				e
			);
			return;
		}

		notification.markSent();
		publishTransitioned(notification, NotificationStatus.SENT);
		log.info(
			"알림 발송 성공. notificationId={}, requestId={}, channel={}, receiver={}, attemptCount={}",
			notification.getId(),
			notification.getRequestId().value(),
			notification.getChannel(),
			notification.getReceiver(),
			notification.getAttemptCount()
		);
	}

	private void handleExternalSendFailure(Notification notification, ExternalNotificationSendException e) {
		if (!e.isRetryable()) {
			notification.markFailed(e.getMessage());
			publishTransitioned(notification, NotificationStatus.FAILED);
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

		if (retryPolicy.isExceeded(notification.getRetryCount())) {
			notification.markDead("재시도 한도 초과: " + e.getMessage());
			publishTransitioned(notification, NotificationStatus.DEAD);
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
		LocalDateTime nextRetryAt = retryPolicy.nextRetryAt(nextRetryCount, LocalDateTime.now());
		notification.markRetryWait(e.getMessage(), e.getErrorCode(), e.getExternalStatusCode(), nextRetryAt);
		publishTransitioned(notification, NotificationStatus.RETRY_WAIT);
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

	private void publishTransitioned(Notification notification, NotificationStatus to) {
		eventPublisher.publishEvent(new NotificationStateTransitioned(
			notification.getId(),
			notification.getChannel(),
			to
		));
	}

	private Notification getNotification(Long notificationId) {
		return notificationRepository.findById(notificationId)
			.orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다"));
	}
}
