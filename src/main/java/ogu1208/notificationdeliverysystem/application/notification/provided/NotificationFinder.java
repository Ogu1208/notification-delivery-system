package ogu1208.notificationdeliverysystem.application.notification.provided;

import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchQuery;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchResult;

public interface NotificationFinder {

	NotificationSearchResult search(NotificationSearchQuery query);
}
