package ogu1208.notificationdeliverysystem.application.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationRegister;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationSender;
import ogu1208.notificationdeliverysystem.application.notification.required.ExternalNotificationSender;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationMessagePublisher;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;

@Service
@Validated
@Transactional
@RequiredArgsConstructor
public class NotificationModifyService implements NotificationRegister, NotificationSender {

	private static final Logger log = LoggerFactory.getLogger(NotificationModifyService.class);

	private final NotificationRepository notificationRepository;
	private final NotificationMessagePublisher notificationMessagePublisher;
	private final ExternalNotificationSender externalNotificationSender;

	@Override
	public Notification register(@Valid NotificationRegisterRequest request) {
		return notificationRepository.findByRequestIdValue(request.requestId())
			.orElseGet(() -> registerNewNotification(request));
	}

	private Notification registerNewNotification(NotificationRegisterRequest request) {
		Notification notification = Notification.register(request);
		Notification savedNotification = notificationRepository.save(notification);
		notificationMessagePublisher.publish(savedNotification);
		return savedNotification;
	}

	@Override
	public void send(Long notificationId) {
		Notification notification = notificationRepository.findById(notificationId)
			.orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다"));
		if (notification.isCompleted()) {
			log.info(
				"완료 상태 알림 발송 건너뜀. notificationId={}, status={}",
				notification.getId(),
				notification.getStatus()
			);
			return;
		}
		notification.startSending();
		try {
			externalNotificationSender.send(notification);
		} catch (RuntimeException e) {
			notification.markFailed(e.getMessage());
			log.warn("알림 발송 실패. notificationId={}, reason={}", notification.getId(), e.getMessage());
			return;
		}
		notification.markSent();
	}
}
