package ogu1208.notificationdeliverysystem.domain.notification.event;

import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;

/**
 * 알림 상태 전이 도메인 이벤트.
 *
 * <p>관측 어댑터가 상태별 카운터를 증가시키는 근거.
 * 도메인 계층에는 Micrometer 타입이 절대 누수되지 않도록 record만 정의한다.
 */
public record NotificationStateTransitioned(
    Long notificationId,
    NotificationChannel channel,
    NotificationStatus to
) {
}
