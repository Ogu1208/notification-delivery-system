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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ogu1208.notificationdeliverysystem.application.notification.required.NotificationMessagePublisher;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

@SpringBootTest
class NotificationOutboxPublishTransactionBoundaryTest {

	@Autowired
	private NotificationOutboxPublishService outboxPublishService;

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
	}

	static class RecordingNotificationMessagePublisher implements NotificationMessagePublisher {

		private boolean transactionActiveWhenPublished;
		private int publishCount;

		@Override
		public void publish(Notification notification) {
			transactionActiveWhenPublished = TransactionSynchronizationManager.isActualTransactionActive();
			publishCount++;
		}
	}
}
