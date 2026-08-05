package ogu1208.notificationdeliverysystem.application.notification.search;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;

public record NotificationSearchQuery(
	String requesterId,
	String receiver,
	Set<NotificationStatus> statuses,
	LocalDateTime from,
	LocalDateTime to,
	NotificationSearchCursor cursor,
	int limit
) {

	public static final int MAX_LIMIT = 100;

	public NotificationSearchQuery {
		boolean hasRequester = requesterId != null && !requesterId.isBlank();
		boolean hasReceiver = receiver != null && !receiver.isBlank();
		if (hasRequester == hasReceiver) {
			throw new IllegalArgumentException("요청자 또는 수신자 중 정확히 하나를 지정해야 합니다");
		}
		if (from == null || to == null) {
			throw new IllegalArgumentException("조회 기간 from, to는 필수입니다");
		}
		if (!from.isBefore(to)) {
			throw new IllegalArgumentException("from은 to보다 앞선 시각이어야 합니다");
		}
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new IllegalArgumentException("limit은 1..%d 범위여야 합니다".formatted(MAX_LIMIT));
		}
		statuses = normalizeStatuses(statuses);
	}

	private static Set<NotificationStatus> normalizeStatuses(Set<NotificationStatus> input) {
		if (input == null || input.isEmpty()) {
			return Collections.emptySet();
		}
		return Collections.unmodifiableSet(EnumSet.copyOf(input));
	}

	public boolean hasRequester() {
		return requesterId != null && !requesterId.isBlank();
	}

	public boolean hasStatusFilter() {
		return !statuses.isEmpty();
	}

	public boolean hasCursor() {
		return cursor != null;
	}
}
