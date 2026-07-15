package ogu1208.notificationdeliverysystem.application.notification.required;

import java.util.Optional;

import org.springframework.data.repository.Repository;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;

public interface NotificationRepository extends Repository<Notification, Long> {

	Notification save(Notification notification);

	Optional<Notification> findById(Long id);

	Optional<Notification> findByRequestIdValue(String requestId);
}
