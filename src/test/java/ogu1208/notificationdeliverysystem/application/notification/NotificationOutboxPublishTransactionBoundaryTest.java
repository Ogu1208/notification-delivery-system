package ogu1208.notificationdeliverysystem.application.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ogu1208.notificationdeliverysystem.adapter.messaging.outbox.OutboxAsyncConfig;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationMessagePublisher;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationRegister;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

@SpringBootTest(properties = {
	"notification.outbox.publisher-enabled=true",
	"spring.main.allow-bean-definition-overriding=true"
})
class NotificationOutboxPublishTransactionBoundaryTest {

	@Autowired
	private NotificationOutboxPublishService outboxPublishService;

	@Autowired
	private NotificationRegister notificationRegister;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private RecordingNotificationMessagePublisher notificationMessagePublisher;

	@Test
	@DisplayName("Kafka 발행은 DB 트랜잭션 밖에서 수행한다")
	void publishesKafkaMessageOutsideDatabaseTransaction() {
		Notification notification = notificationRepository.save(Notification.register(request()));
		outboxEventRepository.save(OutboxEvent.singleNotificationSend(notification.getId()));

		boolean published = outboxPublishService.publishPendingByNotificationId(notification.getId());

		assertThat(published).isTrue();
		assertThat(notificationMessagePublisher.publishCount).isOne();
		assertThat(notificationMessagePublisher.transactionActiveWhenPublished).isFalse();
	}

	@Test
	@DisplayName("알림 등록 commit 직후 Outbox 이벤트를 즉시 발행한다")
	void publishesOutboxEventImmediatelyAfterNotificationRegisterCommit() {
		notificationMessagePublisher.reset();

		Notification notification = notificationRegister.register(request());

		assertThat(notificationMessagePublisher.publishCount).isOne();
		assertThat(notificationMessagePublisher.publishedNotificationId).isEqualTo(notification.getId());
		assertThat(notificationMessagePublisher.transactionActiveWhenPublished).isFalse();
	}

	private NotificationRegisterRequest request() {
		return NotificationFixture.builder()
			.withRequestId("outbox-tx-" + UUID.randomUUID())
			.build();
	}

	@TestConfiguration
	static class TestPublisherConfig {

		@Bean
		@Primary
		RecordingNotificationMessagePublisher recordingNotificationMessagePublisher() {
			return new RecordingNotificationMessagePublisher();
		}

		// commit 직후 발행 검증이 목적이므로 async Executor를 sync로 대체
		@Bean(name = OutboxAsyncConfig.EXECUTOR_BEAN_NAME)
		@Primary
		TaskExecutor outboxAsyncExecutor() {
			return new SyncTaskExecutor();
		}
	}

	static class RecordingNotificationMessagePublisher implements NotificationMessagePublisher {

		private boolean transactionActiveWhenPublished;
		private Long publishedNotificationId;
		private int publishCount;

		@Override
		public void publish(Notification notification) {
			transactionActiveWhenPublished = TransactionSynchronizationManager.isActualTransactionActive();
			publishedNotificationId = notification.getId();
			publishCount++;
		}

		private void reset() {
			transactionActiveWhenPublished = false;
			publishedNotificationId = null;
			publishCount = 0;
		}
	}
}
