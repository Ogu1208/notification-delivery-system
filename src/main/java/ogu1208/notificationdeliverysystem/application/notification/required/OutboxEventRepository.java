package ogu1208.notificationdeliverysystem.application.notification.required;

import java.util.List;

import org.springframework.data.repository.Repository;

import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;

public interface OutboxEventRepository extends Repository<OutboxEvent, Long> {

	OutboxEvent save(OutboxEvent outboxEvent);

	List<OutboxEvent> findByStatus(OutboxEventStatus status);
}
