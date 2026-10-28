package ogu1208.notificationdeliverysystem.adapter.webapi;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.adapter.webapi.dto.NotificationDetailResponse;
import ogu1208.notificationdeliverysystem.adapter.webapi.dto.NotificationRegisterResponse;
import ogu1208.notificationdeliverysystem.adapter.webapi.dto.NotificationSearchParams;
import ogu1208.notificationdeliverysystem.adapter.webapi.dto.NotificationSearchResponse;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationFinder;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationRegister;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchCursor;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchQuery;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchResult;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;

@RestController
@RequiredArgsConstructor
public class NotificationApi {

	private static final int DEFAULT_WINDOW_DAYS = 7;
	private static final int DEFAULT_LIMIT = 20;

	private final NotificationRegister notificationRegister;
	private final NotificationFinder notificationFinder;

	@PostMapping("/notifications")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public NotificationRegisterResponse register(@Valid @RequestBody NotificationRegisterRequest request) {
		Notification notification = notificationRegister.register(request);

		return NotificationRegisterResponse.from(notification);
	}

	@GetMapping("/notifications/{id}")
	public NotificationDetailResponse detail(@PathVariable Long id) {
		Notification notification = notificationFinder.findById(id);
		return NotificationDetailResponse.from(notification);
	}

	@GetMapping("/notifications/by-request/{requestId}")
	public NotificationDetailResponse detailByRequestId(@PathVariable String requestId) {
		Notification notification = notificationFinder.findByRequestId(requestId);
		return NotificationDetailResponse.from(notification);
	}

	@GetMapping("/notifications")
	public NotificationSearchResponse search(@Valid @ModelAttribute NotificationSearchParams params) {
		Set<NotificationStatus> statusSet = params.status() == null ? Set.of() : Set.copyOf(params.status());
		Set<NotificationChannel> channelSet = params.channel() == null ? Set.of() : Set.copyOf(params.channel());
		NotificationSearchCursor searchCursor = params.cursor() == null
			? null
			: NotificationSearchCursor.decode(params.cursor());
		SearchWindow window = resolveWindow(params, searchCursor);
		int limit = params.limit() != null ? params.limit() : DEFAULT_LIMIT;

		NotificationSearchQuery query = new NotificationSearchQuery(
			params.requesterId(), params.receiver(), statusSet, channelSet,
			window.from(), window.to(), searchCursor, limit
		);
		NotificationSearchResult result = notificationFinder.search(query);
		return NotificationSearchResponse.from(result);
	}

	/**
	 * 조회 구간을 결정한다.
	 *
	 * <p>커서가 없는 첫 페이지 요청에서만 {@code now()}로 기본 구간을 계산한다. 커서가 있으면
	 * 첫 페이지에서 확정해 커서에 담아 둔 구간을 그대로 쓴다. 요청마다 다시 계산하면 하한선이
	 * 앞으로 밀려 경계 근처 알림이 어느 페이지에도 나오지 않는다.
	 *
	 * <p>커서와 함께 들어온 {@code from} / {@code to}가 커서의 구간과 다르면 한쪽을 조용히
	 * 무시하지 않고 거부한다. 구간을 바꾸려면 커서를 버리고 첫 페이지부터 다시 조회해야 한다.
	 */
	private static SearchWindow resolveWindow(NotificationSearchParams params, NotificationSearchCursor cursor) {
		if (cursor == null) {
			LocalDateTime to = params.to() != null ? params.to() : LocalDateTime.now();
			LocalDateTime from = params.from() != null ? params.from() : to.minusDays(DEFAULT_WINDOW_DAYS);
			return new SearchWindow(from, to);
		}
		boolean fromMismatch = params.from() != null && !params.from().isEqual(cursor.from());
		boolean toMismatch = params.to() != null && !params.to().isEqual(cursor.to());
		if (fromMismatch || toMismatch) {
			throw new IllegalArgumentException(
				"cursor에 담긴 조회 구간과 from, to가 다릅니다. 구간을 바꾸려면 cursor 없이 다시 조회해야 합니다"
			);
		}
		return new SearchWindow(cursor.from(), cursor.to());
	}

	private record SearchWindow(LocalDateTime from, LocalDateTime to) {
	}
}
