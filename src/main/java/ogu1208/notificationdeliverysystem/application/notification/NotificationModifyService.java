package ogu1208.notificationdeliverysystem.application.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationRegister;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationMessagePublisher;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;

@Service
@Validated
@Transactional
@RequiredArgsConstructor
public class NotificationModifyService implements NotificationRegister {

	private final NotificationRepository notificationRepository;
	private final NotificationMessagePublisher notificationMessagePublisher;

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
}
