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
		LocalDateTime resolvedTo = params.to() != null ? params.to() : LocalDateTime.now();
		LocalDateTime resolvedFrom = params.from() != null
			? params.from()
			: resolvedTo.minusDays(DEFAULT_WINDOW_DAYS);
		Set<NotificationStatus> statusSet = params.status() == null ? Set.of() : Set.copyOf(params.status());
		Set<NotificationChannel> channelSet = params.channel() == null ? Set.of() : Set.copyOf(params.channel());
		NotificationSearchCursor searchCursor = params.cursor() == null
			? null
			: NotificationSearchCursor.decode(params.cursor());
		int limit = params.limit() != null ? params.limit() : DEFAULT_LIMIT;

		NotificationSearchQuery query = new NotificationSearchQuery(
			params.requesterId(), params.receiver(), statusSet, channelSet,
			resolvedFrom, resolvedTo, searchCursor, limit
		);
		NotificationSearchResult result = notificationFinder.search(query);
		return NotificationSearchResponse.from(result);
	}
}
