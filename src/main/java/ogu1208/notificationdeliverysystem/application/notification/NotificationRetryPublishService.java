package ogu1208.notificationdeliverysystem.application.notification;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationMessagePublisher;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRetryPublishService {

	private static final int DEFAULT_BATCH_SIZE = 100;

	private final NotificationRepository notificationRepository;
	private final NotificationMessagePublisher notificationMessagePublisher;

	public int publishDueRetries() {
		return publishDueRetries(DEFAULT_BATCH_SIZE);
	}

	public int publishDueRetries(int limit) {
		List<Notification> notifications = notificationRepository.findRetryWaitDue(LocalDateTime.now(), limit);

		int publishedCount = 0;
		for (Notification notification : notifications) {
			if (publish(notification)) {
				publishedCount++;
			}
		}

		return publishedCount;
	}

	private boolean publish(Notification notification) {
		try {
			notificationMessagePublisher.publish(notification);
			log.info(
				"재시도 Kafka 발행 성공. notificationId={}, requestId={}, retryCount={}",
				notification.getId(),
				notification.getRequestId().value(),
				notification.getRetryCount()
			);
			return true;
		} catch (RuntimeException e) {
			log.warn(
				"알림 재시도 메시지 발행 실패. 다음 알림 발행은 계속 진행합니다. notificationId={}, requestId={}",
				notification.getId(),
				notification.getRequestId().value(),
				e
			);
			return false;
		}
	}
}
