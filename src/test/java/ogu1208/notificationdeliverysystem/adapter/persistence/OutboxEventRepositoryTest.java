package ogu1208.notificationdeliverysystem.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class OutboxEventRepositoryTest {

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Test
	@DisplayName("대기 중인 Outbox 이벤트를 상태로 조회한다")
	void findPendingEventsByStatus() {
		OutboxEvent outboxEvent = outboxEventRepository.save(OutboxEvent.singleNotificationSend(1L));

		assertThat(outboxEvent.getId()).isNotNull();
		assertThat(outboxEventRepository.findByStatus(OutboxEventStatus.PENDING)).contains(outboxEvent);
		assertThat(outboxEventRepository.findByStatus(OutboxEventStatus.PUBLISHED)).isEmpty();
	}
}
