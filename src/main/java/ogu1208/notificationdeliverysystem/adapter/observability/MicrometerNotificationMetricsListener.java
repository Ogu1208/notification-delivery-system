package ogu1208.notificationdeliverysystem.adapter.observability;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import ogu1208.notificationdeliverysystem.domain.notification.event.DispatchLockAttempted;
import ogu1208.notificationdeliverysystem.domain.notification.event.FinalStateSkipped;
import ogu1208.notificationdeliverysystem.domain.notification.event.NotificationStateTransitioned;

/**
 * 알림 도메인 이벤트를 Micrometer 카운터로 변환.
 *
 * <p>도메인/애플리케이션에서 이벤트만 발행하고, 이 어댑터가 지표 이름과 태그를 결정한다.
 * Micrometer 의존은 이 클래스에만 갇힌다.
 */
@Component
public class MicrometerNotificationMetricsListener {

    private final MeterRegistry registry;

    public MicrometerNotificationMetricsListener(MeterRegistry registry) {
        this.registry = registry;
    }

    @EventListener
    public void onStateTransitioned(NotificationStateTransitioned event) {
        registry
            .counter(
                "notification.status.transitions",
                "to", event.to().name(),
                "channel", event.channel().name()
            )
            .increment();
    }

    @EventListener
    public void onDispatchLockAttempted(DispatchLockAttempted event) {
        registry
            .counter(
                "notification.dispatch.lock.acquire",
                "result", event.result().name()
            )
            .increment();
    }

    @EventListener
    public void onFinalStateSkipped(FinalStateSkipped event) {
        registry
            .counter(
                "notification.final.state.skip",
                "status", event.status().name()
            )
            .increment();
    }
}
