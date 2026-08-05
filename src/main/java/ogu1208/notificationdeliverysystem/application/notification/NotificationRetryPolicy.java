package ogu1208.notificationdeliverysystem.application.notification;

import static org.springframework.util.Assert.isTrue;
import static org.springframework.util.Assert.notEmpty;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationRetryPolicy {

	private final int maxCount;
	private final List<Long> backoffSeconds;

	public NotificationRetryPolicy(
		@Value("${notification.retry.max-count:3}") int maxCount,
		@Value("${notification.retry.backoff-seconds:10,20,30}") List<Long> backoffSeconds
	) {
		isTrue(maxCount > 0, "최대 재시도 횟수는 1 이상이어야 합니다");
		notEmpty(backoffSeconds, "재시도 backoff 설정은 필수입니다");
		this.maxCount = maxCount;
		this.backoffSeconds = List.copyOf(backoffSeconds);
	}

	public boolean isExceeded(int retryCount) {
		return retryCount >= maxCount;
	}

	public LocalDateTime nextRetryAt(int nextRetryCount, LocalDateTime now) {
		isTrue(nextRetryCount > 0, "다음 재시도 횟수는 1 이상이어야 합니다");
		int index = Math.min(nextRetryCount, backoffSeconds.size()) - 1;
		return now.plusSeconds(backoffSeconds.get(index));
	}
}
