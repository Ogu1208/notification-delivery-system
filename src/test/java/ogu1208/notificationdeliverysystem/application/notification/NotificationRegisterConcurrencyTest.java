package ogu1208.notificationdeliverysystem.application.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationRegister;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

@SpringBootTest
class NotificationRegisterConcurrencyTest {

	@Autowired
	private NotificationRegister notificationRegister;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	@DisplayName("동시 중복 등록 요청은 예외 없이 같은 알림을 반환한다")
	void registerConcurrentDuplicateRequestsReturnsSameNotification() throws Exception {
		int requestCount = 10;
		String requestId = "concurrent-" + UUID.randomUUID();
		ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
		CountDownLatch ready = new CountDownLatch(requestCount);
		CountDownLatch start = new CountDownLatch(1);

		try {
			List<Future<Notification>> futures = IntStream.range(0, requestCount)
				.mapToObj(index -> executorService.submit(() -> registerAfterStart(requestId, ready, start)))
				.toList();

			assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<Notification> results = futures.stream()
				.map(this::getNotification)
				.toList();

			Long notificationId = results.getFirst().getId();
			assertThat(results)
				.extracting(Notification::getId)
				.containsOnly(notificationId);
			assertThat(countNotificationsByRequestId(requestId)).isOne();
			assertThat(countOutboxEventsByNotificationId(notificationId)).isOne();
		} finally {
			executorService.shutdownNow();
		}
	}

	private Notification registerAfterStart(String requestId, CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		assertThat(start.await(3, TimeUnit.SECONDS)).isTrue();
		return notificationRegister.register(request(requestId));
	}

	private Notification getNotification(Future<Notification> future) {
		try {
			return future.get(5, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new AssertionError("동시 중복 등록 요청 처리 중 예외가 발생했습니다", e);
		}
	}

	private long countNotificationsByRequestId(String requestId) {
		return entityManager.createQuery(
				"select count(n) from Notification n where n.requestId.value = :requestId",
				Long.class
			)
			.setParameter("requestId", requestId)
			.getSingleResult();
	}

	private long countOutboxEventsByNotificationId(Long notificationId) {
		return entityManager.createQuery(
				"select count(e) from OutboxEvent e where e.aggregateId = :notificationId",
				Long.class
			)
			.setParameter("notificationId", notificationId)
			.getSingleResult();
	}

	private NotificationRegisterRequest request(String requestId) {
		return NotificationFixture.builder()
			.withRequestId(requestId)
			.build();
	}
}
