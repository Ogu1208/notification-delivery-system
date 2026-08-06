package ogu1208.notificationdeliverysystem.application.notification.provided;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;

/**
 * 관리자 운영용 유스케이스.
 * 일반 발송 흐름과 구분해 별도 port로 노출한다.
 */
public interface NotificationAdmin {

	/**
	 * DEAD 상태 알림을 수동으로 다시 재시도 대기열에 올린다.
	 *
	 * @return 변경 후 알림
	 * @throws ogu1208.notificationdeliverysystem.application.notification.NotificationNotFoundException 알림 없음
	 * @throws IllegalStateException DEAD가 아님
	 */
	Notification resurrectForRetry(Long id);
}
