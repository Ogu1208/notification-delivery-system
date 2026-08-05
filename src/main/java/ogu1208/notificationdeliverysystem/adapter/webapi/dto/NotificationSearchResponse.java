package ogu1208.notificationdeliverysystem.adapter.webapi.dto;

import java.util.List;

import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchResult;

public record NotificationSearchResponse(
	List<NotificationSearchItem> items,
	String nextCursor
) {

	public static NotificationSearchResponse from(NotificationSearchResult result) {
		List<NotificationSearchItem> items = result.items().stream()
			.map(NotificationSearchItem::from)
			.toList();
		String nextCursor = result.hasNext() ? result.nextCursor().encode() : null;
		return new NotificationSearchResponse(items, nextCursor);
	}
}
