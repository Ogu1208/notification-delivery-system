package ogu1208.notificationdeliverysystem.adapter.persistence;

import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;

@Component
@RequiredArgsConstructor
public class NotificationPersistenceAdapter implements NotificationRepository {

	private final NotificationJpaRepository notificationJpaRepository;

	@Override
	public Notification save(Notification notification) {
		return notificationJpaRepository.save(notification);
	}

	@Override
	public Optional<Notification> findById(Long id) {
		return notificationJpaRepository.findById(id);
	}

	@Override
	public Optional<Notification> findByRequestId(String requestId) {
		return notificationJpaRepository.findByRequestId(requestId);
	}
}
