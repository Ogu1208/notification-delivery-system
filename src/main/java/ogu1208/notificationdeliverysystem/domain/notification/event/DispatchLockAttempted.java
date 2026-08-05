package ogu1208.notificationdeliverysystem.domain.notification.event;

/**
 * Redis dispatch lock 획득 시도 결과 이벤트.
 */
public record DispatchLockAttempted(
    Long notificationId,
    Result result
) {
    public enum Result {
        ACQUIRED,
        MISSED
    }
}
