package ogu1208.notificationdeliverysystem.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;

public interface NotificationJpaRepository extends JpaRepository<Notification, Long> {

	java.util.Optional<Notification> findByRequestId(String requestId);
}
