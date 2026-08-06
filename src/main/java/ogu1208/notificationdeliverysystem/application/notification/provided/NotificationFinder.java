package ogu1208.notificationdeliverysystem.application.notification.provided;

import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchQuery;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchResult;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;

public interface NotificationFinder {

	NotificationSearchResult search(NotificationSearchQuery query);

	Notification findById(Long id);

	Notification findByRequestId(String requestId);
}
