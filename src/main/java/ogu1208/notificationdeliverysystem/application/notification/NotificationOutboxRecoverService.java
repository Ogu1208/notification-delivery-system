package ogu1208.notificationdeliverysystem.application.notification;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationOutboxRecoverService {

	private static final long DEFAULT_PROCESSING_TIMEOUT_MS = 300_000L;

	private final NotificationOutboxModifyService outboxModifyService;

	@Value("${notification.outbox.processing-timeout-ms:" + DEFAULT_PROCESSING_TIMEOUT_MS + "}")
	private long processingTimeoutMs = DEFAULT_PROCESSING_TIMEOUT_MS;

	public int recoverStaleProcessingEvents(int limit) {
		LocalDateTime cutoff = LocalDateTime.now().minus(Duration.ofMillis(processingTimeoutMs));

		return outboxModifyService.recoverStaleProcessingEvents(cutoff, limit);
	}
}
