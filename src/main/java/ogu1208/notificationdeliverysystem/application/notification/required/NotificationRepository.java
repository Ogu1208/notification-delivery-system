package ogu1208.notificationdeliverysystem.application.notification.required;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;

public interface NotificationRepository {

	Notification save(Notification notification);

	Optional<Notification> findById(Long id);

	Optional<Notification> findByRequestId(String requestId);

	List<Notification> findRetryWaitDue(LocalDateTime now, int limit);
}
