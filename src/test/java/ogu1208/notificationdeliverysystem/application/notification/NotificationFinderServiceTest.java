package ogu1208.notificationdeliverysystem.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchQuery;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchResult;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

@ExtendWith(MockitoExtension.class)
class NotificationFinderServiceTest {

	@Mock
	private NotificationRepository notificationRepository;

	@InjectMocks
	private NotificationFinderService notificationFinderService;

	@Test
	@DisplayName("결과가 limit + 1개면 마지막 아이템을 잘라내고 nextCursor를 만든다")
	void buildsNextCursorWhenMoreResults() {
		NotificationSearchQuery query = queryWithLimit(2);
		List<Notification> found = List.of(
			notificationAt(10L, LocalDateTime.of(2026, 8, 5, 12, 0)),
			notificationAt(9L, LocalDateTime.of(2026, 8, 4, 12, 0)),
			notificationAt(8L, LocalDateTime.of(2026, 8, 3, 12, 0))
		);
		when(notificationRepository.search(eq(query), eq(3))).thenReturn(found);

		NotificationSearchResult result = notificationFinderService.search(query);

		assertThat(result.items()).hasSize(2)
			.extracting(Notification::getId)
			.containsExactly(10L, 9L);
		assertThat(result.hasNext()).isTrue();
		assertThat(result.nextCursor().createdAt()).isEqualTo(LocalDateTime.of(2026, 8, 4, 12, 0));
		assertThat(result.nextCursor().id()).isEqualTo(9L);
	}

	@Test
	@DisplayName("결과가 limit 이하면 nextCursor는 null이다")
	void nullCursorWhenNoMoreResults() {
		NotificationSearchQuery query = queryWithLimit(5);
		List<Notification> found = IntStream.range(0, 3)
			.mapToObj(i -> notificationAt((long) (100 - i), LocalDateTime.of(2026, 8, 5 - i, 12, 0)))
			.toList();
		when(notificationRepository.search(any(NotificationSearchQuery.class), eq(6))).thenReturn(found);

		NotificationSearchResult result = notificationFinderService.search(query);

		assertThat(result.items()).hasSize(3);
		assertThat(result.hasNext()).isFalse();
		assertThat(result.nextCursor()).isNull();
	}

	private NotificationSearchQuery queryWithLimit(int limit) {
		return new NotificationSearchQuery(
			"requester-1", null, Set.of(),
			LocalDateTime.of(2026, 8, 1, 0, 0),
			LocalDateTime.of(2026, 8, 8, 0, 0),
			null, limit
		);
	}

	private Notification notificationAt(Long id, LocalDateTime createdAt) {
		NotificationRegisterRequest request = NotificationFixture.builder()
			.withRequestId("req-" + id)
			.build();
		Notification entity = Notification.register(request);
		ReflectionTestUtils.setField(entity, "id", id);
		ReflectionTestUtils.setField(entity, "createdAt", createdAt);
		return entity;
	}
}
