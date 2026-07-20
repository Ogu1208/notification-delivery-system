package ogu1208.notificationdeliverysystem.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxAggregateType;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;

@DataJpaTest
@Import({JpaAuditingConfig.class, OutboxEventPersistenceAdapter.class})
class OutboxEventRepositoryTest {

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("대기 중인 Outbox 이벤트를 상태와 ID 순서로 제한 조회한다")
	void findPendingEventsByStatusWithLimit() {
		OutboxEvent outboxEvent = outboxEventRepository.save(OutboxEvent.singleNotificationSend(1L));

		assertThat(outboxEvent.getId()).isNotNull();
		assertThat(outboxEvent.getAggregateType()).isEqualTo(OutboxAggregateType.NOTIFICATION);
		assertThat(outboxEventRepository.findPendingOrderByIdAsc(100)).contains(outboxEvent);
		assertThat(outboxEventRepository.findByAggregateTypeAndAggregateIdAndStatus(
			OutboxAggregateType.NOTIFICATION,
			1L,
			OutboxEventStatus.PENDING
		))
			.contains(outboxEvent);
	}

	@Test
	@DisplayName("오래 처리 중인 Outbox 이벤트를 상태와 수정 시각으로 제한 조회한다")
	void findStaleProcessingEventsByStatusAndUpdatedAtWithLimit() {
		OutboxEvent staleEvent = outboxEventRepository.save(OutboxEvent.singleNotificationSend(1L));
		staleEvent.startPublishing();
		outboxEventRepository.save(staleEvent);
		OutboxEvent freshEvent = outboxEventRepository.save(OutboxEvent.singleNotificationSend(2L));
		freshEvent.startPublishing();
		outboxEventRepository.save(freshEvent);
		entityManager.flush();

		LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
		jdbcTemplate.update(
			"update outbox_event set updated_at = ? where id = ?",
			Timestamp.valueOf(cutoff.minusMinutes(1)),
			staleEvent.getId()
		);
		jdbcTemplate.update(
			"update outbox_event set updated_at = ? where id = ?",
			Timestamp.valueOf(cutoff.plusMinutes(1)),
			freshEvent.getId()
		);
		entityManager.clear();

		List<OutboxEvent> events = outboxEventRepository.findProcessingUpdatedBefore(cutoff, 100);

		assertThat(events)
			.extracting(OutboxEvent::getId)
			.containsExactly(staleEvent.getId());
	}

	@Test
	@DisplayName("Outbox polling과 단건 즉시 발행 조회를 위한 인덱스를 생성한다")
	void createsOutboxLookupIndexes() {
		List<String> indexNames = jdbcTemplate.queryForList(
			"select index_name from information_schema.indexes where table_name = 'OUTBOX_EVENT'",
			String.class
		);

		assertThat(indexNames).contains(
			"IDX_OUTBOX_EVENT_STATUS_ID",
			"IDX_OUTBOX_EVENT_STATUS_UPDATED_AT",
			"IDX_OUTBOX_EVENT_AGGREGATE_STATUS"
		);
	}
}
