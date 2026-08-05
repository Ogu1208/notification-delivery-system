package ogu1208.notificationdeliverysystem.application.notification;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;

@Service
@RequiredArgsConstructor
public class NotificationOutboxModifyService {

	private final OutboxEventRepository outboxEventRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int recoverStaleProcessingEvents(LocalDateTime cutoff, int limit) {
		List<OutboxEvent> processingEvents = outboxEventRepository.findProcessingUpdatedBefore(cutoff, limit);

		for (OutboxEvent outboxEvent : processingEvents) {
			outboxEvent.markPending();
			outboxEventRepository.save(outboxEvent);
		}

		return processingEvents.size();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean markProcessingIfPending(Long outboxEventId) {
		return outboxEventRepository.markProcessingIfPending(outboxEventId);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markProcessing(Long outboxEventId) {
		OutboxEvent outboxEvent = getOutboxEvent(outboxEventId);

		outboxEvent.startPublishing();
		outboxEventRepository.save(outboxEvent);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markPublished(Long outboxEventId) {
		OutboxEvent outboxEvent = getOutboxEvent(outboxEventId);

		outboxEvent.markPublished();
		outboxEventRepository.save(outboxEvent);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
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
