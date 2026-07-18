package ogu1208.notificationdeliverysystem.application.notification.required;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;

public interface NotificationRepository extends Repository<Notification, Long> {

	Notification save(Notification notification);

	Optional<Notification> findById(Long id);

	@Query("select n from Notification n where n.requestId.value = :requestId")
	Optional<Notification> findByRequestId(String requestId);
}
