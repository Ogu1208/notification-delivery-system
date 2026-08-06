package ogu1208.notificationdeliverysystem.application.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationAdmin;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationAdminService implements NotificationAdmin {

	private final NotificationRepository notificationRepository;

	@Override
	@Transactional
	public Notification resurrectForRetry(Long id) {
		Notification notification = notificationRepository.findById(id)
			.orElseThrow(() -> new NotificationNotFoundException(id));

		notification.resurrectForRetry();
		Notification updated = notificationRepository.save(notification);

		log.info(
			"관리자 수동 재시도. notificationId={}, requestId={}, retryCount={}",
			updated.getId(),
			updated.getRequestId().value(),
			updated.getRetryCount()
		);

		return updated;
	}
}
