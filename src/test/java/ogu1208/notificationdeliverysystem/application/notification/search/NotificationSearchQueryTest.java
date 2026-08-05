package ogu1208.notificationdeliverysystem.application.notification.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;

class NotificationSearchQueryTest {

	private final LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
	private final LocalDateTime to = LocalDateTime.of(2026, 8, 8, 0, 0);

	@Test
	@DisplayName("요청자 또는 수신자가 정확히 하나 없으면 예외를 던진다")
	void rejectsWhenBothOrNeitherKeyPresent() {
		assertThatThrownBy(() -> new NotificationSearchQuery(
			null, null, Set.of(), from, to, null, 20
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("요청자 또는 수신자 중 정확히 하나를 지정해야 합니다");

		assertThatThrownBy(() -> new NotificationSearchQuery(
			"requester-1", "user@example.com", Set.of(), from, to, null, 20
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("요청자 또는 수신자 중 정확히 하나를 지정해야 합니다");
	}

	@Test
	@DisplayName("limit이 허용 범위를 벗어나면 예외를 던진다")
	void rejectsInvalidLimit() {
		assertThatThrownBy(() -> new NotificationSearchQuery(
			"requester-1", null, Set.of(), from, to, null, 0
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("limit은 1..100 범위여야 합니다");

		assertThatThrownBy(() -> new NotificationSearchQuery(
			"requester-1", null, Set.of(), from, to, null, 101
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("limit은 1..100 범위여야 합니다");
	}

	@Test
	@DisplayName("from은 to보다 앞선 시각이어야 한다")
	void rejectsInvalidRange() {
		assertThatThrownBy(() -> new NotificationSearchQuery(
			"requester-1", null, Set.of(), to, from, null, 20
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("from은 to보다 앞선 시각이어야 합니다");
	}

	@Test
	@DisplayName("상태 필터는 방어적 복사로 불변 집합이 된다")
	void statusesAreDefensivelyCopied() {
		EnumSet<NotificationStatus> input = EnumSet.of(NotificationStatus.SENT);
		NotificationSearchQuery query = new NotificationSearchQuery(
			"requester-1", null, input, from, to, null, 20
		);

		assertThat(query.statuses()).containsExactly(NotificationStatus.SENT);
		assertThatThrownBy(() -> query.statuses().add(NotificationStatus.FAILED))
			.isInstanceOf(UnsupportedOperationException.class);
	}
}
