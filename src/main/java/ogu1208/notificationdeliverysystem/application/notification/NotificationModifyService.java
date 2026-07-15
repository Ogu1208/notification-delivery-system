package ogu1208.notificationdeliverysystem.application.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationRegister;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationRegisterRequest;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationMessagePublisher;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;

@Service
@Validated
@Transactional
public class NotificationModifyService implements NotificationRegister {

	private final NotificationRepository notificationRepository;
	private final NotificationMessagePublisher notificationMessagePublisher;

	public NotificationModifyService(
		NotificationRepository notificationRepository,
		NotificationMessagePublisher notificationMessagePublisher
	) {
		this.notificationRepository = notificationRepository;
		this.notificationMessagePublisher = notificationMessagePublisher;
	}

	@Override
	public Notification register(@Valid NotificationRegisterRequest request) {
		return notificationRepository.findByRequestId(request.requestId())
			.orElseGet(() -> registerNewNotification(request));
	}

	private Notification registerNewNotification(NotificationRegisterRequest request) {
		Notification notification = Notification.register(
			request.requestId(),
			request.receiver(),
			request.channel(),
			request.title(),
			request.content()
		);
		Notification savedNotification = notificationRepository.save(notification);
		notificationMessagePublisher.publish(savedNotification);
		return savedNotification;
	}
}
