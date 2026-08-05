package ogu1208.notificationdeliverysystem.adapter.messaging.outbox;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Outbox {@code AFTER_COMMIT} listener 전용 Executor.
 *
 * <p>Kafka 발행을 호출 스레드에서 분리하여, 발행 지연이 요청 스레드의
 * Hikari 커넥션 점유 시간을 늘리지 않도록 한다.
 *
 * <p>Pool 크기는 DataSource pool을 기준으로 잡는다. 백그라운드 태스크도
 * 발행 시 동일한 JDBC 커넥션을 사용하기 때문이다. Reject된 태스크는
 * {@link NotificationOutboxRecoveryPoller}가 복구한다.
 */
@Configuration
@EnableAsync
@ConditionalOnProperty(name = "notification.outbox.publisher-enabled", havingValue = "true")
public class OutboxAsyncConfig {

	public static final String EXECUTOR_BEAN_NAME = "outboxAsyncExecutor";

	@Bean(name = EXECUTOR_BEAN_NAME)
	public ThreadPoolTaskExecutor outboxAsyncExecutor(
		@Value("${notification.outbox.async.core-pool-size:8}") int corePoolSize,
		@Value("${notification.outbox.async.max-pool-size:20}") int maxPoolSize,
		@Value("${notification.outbox.async.queue-capacity:500}") int queueCapacity,
		@Value("${notification.outbox.async.await-termination-seconds:10}") int awaitTerminationSeconds
	) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(corePoolSize);
		executor.setMaxPoolSize(maxPoolSize);
		executor.setQueueCapacity(queueCapacity);
		executor.setThreadNamePrefix("outbox-async-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
		executor.initialize();
		return executor;
	}
}
