package ogu1208.notificationdeliverysystem.application.notification.required;

public interface OutboxEventNotifier {

	void notifySaved(Long notificationId);
}
