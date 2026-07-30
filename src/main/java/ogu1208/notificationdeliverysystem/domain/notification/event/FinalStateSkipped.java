package ogu1208.notificationdeliverysystem.domain.notification.event;

import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;

/**
 * 이미 최종 상태(SENT/FAILED/DEAD)인 알림에 대해 발송을 건너뛴 이벤트.
 * at-least-once 재전달 방어가 실제로 발동한 횟수를 관측할 근거.
 */
public record FinalStateSkipped(
    Long notificationId,
    NotificationStatus status
) {
}
