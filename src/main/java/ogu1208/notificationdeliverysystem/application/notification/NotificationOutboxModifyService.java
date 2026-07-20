package ogu1208.notificationdeliverysystem.application.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;

@Service
@RequiredArgsConstructor
public class NotificationOutboxModifyService {

	private final OutboxEventRepository outboxEventRepository;

	@Transactional
	public void markProcessing(Long outboxEventId) {
		OutboxEvent outboxEvent = getOutboxEvent(outboxEventId);

		outboxEvent.startPublishing();
		outboxEventRepository.save(outboxEvent);
	}

	@Transactional
	public void markPublished(Long outboxEventId) {
		OutboxEvent outboxEvent = getOutboxEvent(outboxEventId);

		outboxEvent.markPublished();
		outboxEventRepository.save(outboxEvent);
	}

	@Transactional
	public void markPending(Long outboxEventId) {
		OutboxEvent outboxEvent = getOutboxEvent(outboxEventId);

		outboxEvent.markPending();
		outboxEventRepository.save(outboxEvent);
	}

	private OutboxEvent getOutboxEvent(Long outboxEventId) {
		return outboxEventRepository.findById(outboxEventId)
			.orElseThrow(() -> new IllegalStateException("Outbox 이벤트를 찾을 수 없습니다"));
	}
}
