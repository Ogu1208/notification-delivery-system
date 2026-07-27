package ogu1208.notificationdeliverysystem.adapter.integration;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import ogu1208.notificationdeliverysystem.application.notification.required.DispatchLock;
import ogu1208.notificationdeliverysystem.application.notification.required.DispatchLockManager;

@Component
@ConditionalOnProperty(name = "notification.dispatch-lock.type", havingValue = "redis")
public class RedisDispatchLockManager implements DispatchLockManager {

	private static final Logger log = LoggerFactory.getLogger(RedisDispatchLockManager.class);

	private static final String KEY_PREFIX = "dispatch-lock:";
	private static final String RELEASE_SCRIPT = """
		if redis.call('get', KEYS[1]) == ARGV[1] then
			return redis.call('del', KEYS[1])
		end
		return 0
		""";

	private final RedisTemplate<String, String> redisTemplate;
	private final long ttlMs;
	private final RedisScript<Long> releaseScript;

	public RedisDispatchLockManager(
		RedisTemplate<String, String> redisTemplate,
		@Value("${notification.dispatch-lock.ttl-ms:30000}") long ttlMs
	) {
		this.redisTemplate = redisTemplate;
		this.ttlMs = ttlMs;
		this.releaseScript = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);
	}

	@Override
	public Optional<DispatchLock> tryAcquire(Long notificationId) {
		String key = KEY_PREFIX + notificationId;
		String token = UUID.randomUUID().toString();
		Boolean acquired = redisTemplate.opsForValue()
			.setIfAbsent(key, token, Duration.ofMillis(ttlMs));

		if (!Boolean.TRUE.equals(acquired)) {
			log.debug("Dispatch 락 획득 실패. notificationId={}, key={}", notificationId, key);
			return Optional.empty();
		}

		log.debug("Dispatch 락 획득. notificationId={}, key={}, ttlMs={}", notificationId, key, ttlMs);
		return Optional.of(new RedisDispatchLock(key, token));
	}

	private class RedisDispatchLock implements DispatchLock {

		private final String key;
		private final String token;

		private RedisDispatchLock(String key, String token) {
			this.key = key;
			this.token = token;
		}

		@Override
		public void close() {
			Long released = redisTemplate.execute(releaseScript, List.of(key), token);
			if (released == null || released == 0L) {
				log.debug("Dispatch 락 해제 시점에 내 토큰이 아님. key={} (락이 이미 TTL로 만료되었을 가능성)", key);
			} else {
				log.debug("Dispatch 락 해제. key={}", key);
			}
		}
	}
}
