package ogu1208.notificationdeliverysystem.application.notification;

/**
 * 요청한 알림을 찾을 수 없을 때 발생. HTTP 404로 매핑된다.
 */
public class NotificationNotFoundException extends RuntimeException {

	public NotificationNotFoundException(Long notificationId) {
		super("알림을 찾을 수 없습니다. notificationId=" + notificationId);
	}
}
