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
	@DisplayName("요청자와 수신자가 모두 없으면 예외를 던진다")
	void rejectsWhenNeitherKeyPresent() {
		assertThatThrownBy(() -> new NotificationSearchQuery(
			null, null, Set.of(), Set.of(), from, to, null, 20
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("요청자 또는 수신자 중 최소 하나는 지정해야 합니다");
	}

	@Test
	@DisplayName("요청자와 수신자를 함께 지정하는 것도 허용된다")
	void acceptsBothKeys() {
		NotificationSearchQuery query = new NotificationSearchQuery(
			"requester-1", "user@example.com", Set.of(), Set.of(), from, to, null, 20
		);

		assertThat(query.hasRequester()).isTrue();
		assertThat(query.hasReceiver()).isTrue();
	}

	@Test
	@DisplayName("limit이 허용 범위를 벗어나면 예외를 던진다")
	void rejectsInvalidLimit() {
		assertThatThrownBy(() -> new NotificationSearchQuery(
			"requester-1", null, Set.of(), Set.of(), from, to, null, 0
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("limit은 1..100 범위여야 합니다");

		assertThatThrownBy(() -> new NotificationSearchQuery(
			"requester-1", null, Set.of(), Set.of(), from, to, null, 101
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("limit은 1..100 범위여야 합니다");
	}

	@Test
	@DisplayName("from은 to보다 앞선 시각이어야 한다")
	void rejectsInvalidRange() {
		assertThatThrownBy(() -> new NotificationSearchQuery(
			"requester-1", null, Set.of(), Set.of(), to, from, null, 20
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("from은 to보다 앞선 시각이어야 합니다");
	}

	@Test
	@DisplayName("상태 필터는 방어적 복사로 불변 집합이 된다")
	void statusesAreDefensivelyCopied() {
		EnumSet<NotificationStatus> input = EnumSet.of(NotificationStatus.SENT);
		NotificationSearchQuery query = new NotificationSearchQuery(
			"requester-1", null, input, Set.of(), from, to, null, 20
		);

		assertThat(query.statuses()).containsExactly(NotificationStatus.SENT);
		assertThatThrownBy(() -> query.statuses().add(NotificationStatus.FAILED))
			.isInstanceOf(UnsupportedOperationException.class);
	}
}
