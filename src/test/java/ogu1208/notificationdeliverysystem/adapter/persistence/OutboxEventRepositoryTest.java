package ogu1208.notificationdeliverysystem.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class OutboxEventRepositoryTest {

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("대기 중인 Outbox 이벤트를 상태와 ID 순서로 제한 조회한다")
	void findPendingEventsByStatusWithLimit() {
		OutboxEvent outboxEvent = outboxEventRepository.save(OutboxEvent.singleNotificationSend(1L));

		assertThat(outboxEvent.getId()).isNotNull();
		assertThat(outboxEventRepository.findByStatusOrderByIdAsc(
			OutboxEventStatus.PENDING,
			PageRequest.of(0, 100)
		)).contains(outboxEvent);
		assertThat(outboxEventRepository.findByAggregateIdAndStatus(1L, OutboxEventStatus.PENDING))
			.contains(outboxEvent);
		assertThat(outboxEventRepository.findByStatusOrderByIdAsc(
			OutboxEventStatus.PUBLISHED,
			PageRequest.of(0, 100)
		)).isEmpty();
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
			"IDX_OUTBOX_EVENT_AGGREGATE_STATUS"
		);
	}
}
