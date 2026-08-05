package ogu1208.notificationdeliverysystem.adapter.webapi;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.adapter.webapi.dto.NotificationRegisterResponse;
import ogu1208.notificationdeliverysystem.adapter.webapi.dto.NotificationSearchResponse;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationFinder;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationRegister;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchCursor;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchQuery;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchResult;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
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

	@GetMapping("/notifications")
	public NotificationSearchResponse search(
		@RequestParam(required = false) String requesterId,
		@RequestParam(required = false) String receiver,
		@RequestParam(name = "status", required = false) List<NotificationStatus> statuses,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
		@RequestParam(required = false) String cursor,
		@RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit
	) {
		LocalDateTime resolvedTo = to != null ? to : LocalDateTime.now();
		LocalDateTime resolvedFrom = from != null ? from : resolvedTo.minusDays(DEFAULT_WINDOW_DAYS);
		Set<NotificationStatus> statusSet = statuses == null ? Set.of() : Set.copyOf(statuses);
		NotificationSearchCursor searchCursor = cursor == null ? null : NotificationSearchCursor.decode(cursor);

		NotificationSearchQuery query = new NotificationSearchQuery(
			requesterId, receiver, statusSet, resolvedFrom, resolvedTo, searchCursor, limit
		);
		NotificationSearchResult result = notificationFinder.search(query);
		return NotificationSearchResponse.from(result);
	}
}
