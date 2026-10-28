package ogu1208.notificationdeliverysystem.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
	@DisplayName("nextCursor는 요청에 쓰인 조회 구간을 함께 담는다")
	void nextCursorCarriesSearchWindow() {
		NotificationSearchQuery query = queryWithLimit(1);
		List<Notification> found = List.of(
			notificationAt(10L, LocalDateTime.of(2026, 8, 5, 12, 0)),
			notificationAt(9L, LocalDateTime.of(2026, 8, 4, 12, 0))
		);
		when(notificationRepository.search(eq(query), eq(2))).thenReturn(found);

		NotificationSearchResult result = notificationFinderService.search(query);

		assertThat(result.nextCursor().from()).isEqualTo(query.from());
		assertThat(result.nextCursor().to()).isEqualTo(query.to());
	}

	@Test
	@DisplayName("결과가 limit보다 적으면 nextCursor는 null이다")
	void nullCursorWhenFewerThanLimit() {
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

	@Test
	@DisplayName("결과가 정확히 limit개면 nextCursor는 null이다 (다음 페이지 존재 여부는 알 수 없음)")
	void nullCursorWhenExactlyLimit() {
		NotificationSearchQuery query = queryWithLimit(3);
		List<Notification> found = IntStream.range(0, 3)
			.mapToObj(i -> notificationAt((long) (100 - i), LocalDateTime.of(2026, 8, 5 - i, 12, 0)))
			.toList();
		// limit+1=4를 요청했지만 3개만 반환됨 → 정확히 limit개
		when(notificationRepository.search(any(NotificationSearchQuery.class), eq(4))).thenReturn(found);

		NotificationSearchResult result = notificationFinderService.search(query);

		assertThat(result.items()).hasSize(3);
		assertThat(result.hasNext()).isFalse();
		assertThat(result.nextCursor()).isNull();
	}

	@Test
	@DisplayName("findById는 저장소에서 알림을 조회해 반환한다")
	void findByIdReturnsNotification() {
		Notification notification = notificationAt(42L, LocalDateTime.of(2026, 8, 5, 12, 0));
		when(notificationRepository.findById(42L)).thenReturn(Optional.of(notification));

		Notification found = notificationFinderService.findById(42L);

		assertThat(found).isSameAs(notification);
	}

	@Test
	@DisplayName("findById로 존재하지 않는 알림을 조회하면 NotificationNotFoundException을 던진다")
	void findByIdThrowsWhenMissing() {
		when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> notificationFinderService.findById(999L))
			.isInstanceOf(NotificationNotFoundException.class)
			.hasMessageContaining("notificationId=999");
	}

	@Test
	@DisplayName("findByRequestId는 저장소에서 알림을 조회해 반환한다")
	void findByRequestIdReturnsNotification() {
		Notification notification = notificationAt(42L, LocalDateTime.of(2026, 8, 5, 12, 0));
		when(notificationRepository.findByRequestId("req-abc")).thenReturn(Optional.of(notification));

		Notification found = notificationFinderService.findByRequestId("req-abc");

		assertThat(found).isSameAs(notification);
	}

	@Test
	@DisplayName("findByRequestId로 존재하지 않는 알림을 조회하면 NotificationNotFoundException을 던진다")
	void findByRequestIdThrowsWhenMissing() {
		when(notificationRepository.findByRequestId("req-missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> notificationFinderService.findByRequestId("req-missing"))
			.isInstanceOf(NotificationNotFoundException.class)
			.hasMessageContaining("requestId=req-missing");
	}

	private NotificationSearchQuery queryWithLimit(int limit) {
		return new NotificationSearchQuery(
			"requester-1", null, Set.of(), Set.of(),
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
