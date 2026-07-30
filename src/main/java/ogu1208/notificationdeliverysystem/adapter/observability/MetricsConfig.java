package ogu1208.notificationdeliverysystem.adapter.observability;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;

/**
 * Micrometer 관측 설정.
 *
 * <ul>
 *   <li>{@link TimedAspect}, {@link CountedAspect} Bean 등록으로 {@code @Timed}/{@code @Counted} 어노테이션을 활성화.
 *   <li>{@code notification.outbox.pending.count} Gauge 등록으로 발행 대기 백로그를 실시간 관측.
 * </ul>
 */
@Configuration
public class MetricsConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public CountedAspect countedAspect(MeterRegistry registry) {
        return new CountedAspect(registry);
    }

    /**
     * PENDING 상태 Outbox 이벤트 수를 Gauge로 등록.
     * Gauge는 스크레이핑 시점에 supplier를 호출하므로 항상 최신값을 반환한다.
     *
     * <p>Bean으로 두어 라이프사이클에 참여시키고 참조를 유지한다.
     * MeterRegistry가 supplier를 WeakReference로 보관하기 때문에 강한 참조가 필요.
     */
    @Bean
    public OutboxPendingGauge outboxPendingGauge(
        MeterRegistry registry,
        OutboxEventRepository outboxEventRepository
    ) {
        OutboxPendingGauge gauge = new OutboxPendingGauge(outboxEventRepository);
        registry.gauge("notification.outbox.pending.count", gauge, OutboxPendingGauge::value);
        return gauge;
    }

    static class OutboxPendingGauge {
        private final OutboxEventRepository outboxEventRepository;

        OutboxPendingGauge(OutboxEventRepository outboxEventRepository) {
            this.outboxEventRepository = outboxEventRepository;
        }

        double value() {
            return outboxEventRepository.countByStatus(OutboxEventStatus.PENDING);
        }
    }
}
