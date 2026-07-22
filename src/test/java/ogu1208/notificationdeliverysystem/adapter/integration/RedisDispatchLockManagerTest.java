package ogu1208.notificationdeliverysystem.adapter.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import ogu1208.notificationdeliverysystem.application.notification.required.DispatchLock;

@SuppressWarnings("unchecked")
class RedisDispatchLockManagerTest {

	private final RedisTemplate<String, String> redisTemplate = mock();
	private final ValueOperations<String, String> valueOperations = mock();
	private final RedisDispatchLockManager dispatchLockManager = new RedisDispatchLockManager(
		redisTemplate,
		30_000
	);

	@Test
	@DisplayName("Redis에 발송 락 키가 없으면 TTL과 함께 락을 획득한다")
	void tryAcquireReturnsLockWhenRedisLockKeyDoesNotExist() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(eq("dispatch-lock:1"), any(), eq(Duration.ofMillis(30_000))))
			.thenReturn(true);

		Optional<DispatchLock> lock = dispatchLockManager.tryAcquire(1L);

		assertThat(lock).isPresent();
	}

	@Test
	@DisplayName("Redis에 발송 락 키가 이미 있으면 락을 획득하지 못한다")
	void tryAcquireReturnsEmptyWhenRedisLockKeyAlreadyExists() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(eq("dispatch-lock:1"), any(), eq(Duration.ofMillis(30_000))))
			.thenReturn(false);

		Optional<DispatchLock> lock = dispatchLockManager.tryAcquire(1L);

		assertThat(lock).isEmpty();
	}

	@Test
	@DisplayName("락 해제 시 내가 잡은 락인지 Redis 값 비교 후 삭제한다")
	void closeReleasesLockWithOwnerTokenCheck() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(eq("dispatch-lock:1"), any(), eq(Duration.ofMillis(30_000))))
			.thenReturn(true);
		DispatchLock lock = dispatchLockManager.tryAcquire(1L).orElseThrow();

		lock.close();

		verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("dispatch-lock:1")), any());
	}
}
