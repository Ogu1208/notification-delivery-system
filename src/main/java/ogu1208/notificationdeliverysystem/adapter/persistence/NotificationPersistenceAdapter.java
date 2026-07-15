package ogu1208.notificationdeliverysystem.adapter.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;

@Repository
public class NotificationPersistenceAdapter implements NotificationRepository {

	private final NotificationJpaRepository notificationJpaRepository;

	public NotificationPersistenceAdapter(NotificationJpaRepository notificationJpaRepository) {
		this.notificationJpaRepository = notificationJpaRepository;
	}

	@Override
	public Notification save(Notification notification) {
		return notificationJpaRepository.save(notification);
	}

	@Override
	public Optional<Notification> findByRequestId(String requestId) {
		return notificationJpaRepository.findByRequestId(requestId);
	}
}
