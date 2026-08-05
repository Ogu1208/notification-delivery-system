package ogu1208.notificationdeliverysystem.application.notification.search;

import java.util.List;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;

public record NotificationSearchResult(
	List<Notification> items,
	NotificationSearchCursor nextCursor
) {

	public boolean hasNext() {
		return nextCursor != null;
	}
}
