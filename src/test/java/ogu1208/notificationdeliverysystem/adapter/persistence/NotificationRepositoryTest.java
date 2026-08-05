package ogu1208.notificationdeliverysystem.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture.createRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;

import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchCursor;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchQuery;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

@DataJpaTest
@Import({JpaAuditingConfig.class, NotificationPersistenceAdapter.class, NotificationSearchDao.class})
class NotificationRepositoryTest {

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	@DisplayName("알림을 저장하고 요청 ID로 조회한다")
	void saveAndFindByRequestId() {
		Notification notification = Notification.register(createRequest());

		Notification savedNotification = notificationRepository.save(notification);

		assertThat(savedNotification.getId()).isNotNull();
		assertThat(savedNotification.getCreatedAt()).isNotNull();
		assertThat(savedNotification.getUpdatedAt()).isNotNull();
		assertThat(notificationRepository.findByRequestId("req-001")).contains(savedNotification);
	}

	@Test
	@DisplayName("요청 ID는 중복 저장할 수 없다")
	void requestIdMustBeUnique() {
		notificationRepository.save(Notification.register(createRequest()));
		entityManager.flush();

		NotificationRegisterRequest duplicateRequest = NotificationFixture.builder()
			.withReceiver("other@example.com")
			.withChannel(NotificationChannel.SMS)
			.withTitle("retry")
			.build();

		assertThatThrownBy(() -> notificationRepository.save(Notification.register(duplicateRequest)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("재시도 시간이 지난 RETRY_WAIT 알림만 제한 개수만큼 조회한다")
	void findRetryWaitDue() {
		LocalDateTime now = LocalDateTime.now();
		Notification firstDue = retryWaitNotification("req-101", now.minusSeconds(10));
		Notification secondDue = retryWaitNotification("req-102", now.minusSeconds(5));
		Notification notDue = retryWaitNotification("req-103", now.plusSeconds(10));
		Notification sent = Notification.register(NotificationFixture.builder()
			.withRequestId("req-104")
			.build());
		sent.startProcessing();
		sent.markSent();
		notificationRepository.save(firstDue);
		notificationRepository.save(secondDue);
		notificationRepository.save(notDue);
		notificationRepository.save(sent);
		entityManager.flush();
		entityManager.clear();

		List<Notification> notifications = notificationRepository.findRetryWaitDue(now, 1);

		assertThat(notifications).hasSize(1);
		assertThat(notifications.get(0).getRequestId().value()).isEqualTo("req-101");
		assertThat(notifications.get(0).getStatus()).isEqualTo(NotificationStatus.RETRY_WAIT);
	}

	private Notification retryWaitNotification(String requestId, LocalDateTime nextRetryAt) {
		Notification notification = Notification.register(NotificationFixture.builder()
			.withRequestId(requestId)
			.build());
		notification.startProcessing();
		notification.markRetryWait("mock api unavailable", "MOCK_UNAVAILABLE", 503, nextRetryAt);
		return notification;
	}

	@Test
	@DisplayName("요청자 기준 조회는 기간 안의 알림만 최신순으로 반환한다")
	void searchByRequesterOrdersDesc() {
		saveWith("req-201", "requester-A", "user1@example.com");
		saveWith("req-202", "requester-A", "user2@example.com");
		saveWith("req-203", "requester-B", "user3@example.com");
		entityManager.flush();
		entityManager.clear();

		LocalDateTime now = LocalDateTime.now();
		NotificationSearchQuery query = new NotificationSearchQuery(
			"requester-A", null, Set.of(),
			now.minusDays(1), now.plusDays(1),
			null, 20
		);

		List<Notification> found = notificationRepository.search(query, query.limit() + 1);

		assertThat(found).extracting(n -> n.getRequestId().value())
			.containsExactly("req-202", "req-201");
	}

	@Test
	@DisplayName("수신자 기준 조회도 동일한 정렬 규칙을 따른다")
	void searchByReceiver() {
		saveWith("req-301", "requester-A", "target@example.com");
		saveWith("req-302", "requester-B", "target@example.com");
		saveWith("req-303", "requester-A", "other@example.com");
		entityManager.flush();
		entityManager.clear();

		LocalDateTime now = LocalDateTime.now();
		NotificationSearchQuery query = new NotificationSearchQuery(
			null, "target@example.com", Set.of(),
			now.minusDays(1), now.plusDays(1),
			null, 20
		);

		List<Notification> found = notificationRepository.search(query, query.limit() + 1);

		assertThat(found).extracting(n -> n.getRequestId().value())
			.containsExactly("req-302", "req-301");
	}

	@Test
	@DisplayName("상태 필터를 주면 해당 상태 알림만 반환한다")
	void searchWithStatusFilter() {
		Notification pending = save(NotificationFixture.builder().withRequestId("req-401").build());
		Notification sent = save(NotificationFixture.builder().withRequestId("req-402").build());
		sent.startProcessing();
		sent.markSent();
		notificationRepository.save(sent);
		entityManager.flush();
		entityManager.clear();

		LocalDateTime now = LocalDateTime.now();
		NotificationSearchQuery query = new NotificationSearchQuery(
			"requester-001", null, Set.of(NotificationStatus.SENT),
			now.minusDays(1), now.plusDays(1),
			null, 20
		);

		List<Notification> found = notificationRepository.search(query, query.limit() + 1);

		assertThat(found).hasSize(1);
		assertThat(found.get(0).getRequestId().value()).isEqualTo("req-402");
		assertThat(pending).isNotNull(); // used only to keep visible baseline row
	}

	@Test
	@DisplayName("커서를 지정하면 커서보다 오래된 결과만 반환한다")
	void searchWithCursor() {
		Notification first = save(NotificationFixture.builder().withRequestId("req-501").build());
		Notification second = save(NotificationFixture.builder().withRequestId("req-502").build());
		Notification third = save(NotificationFixture.builder().withRequestId("req-503").build());
		entityManager.flush();
		entityManager.clear();

		LocalDateTime now = LocalDateTime.now();
		NotificationSearchCursor cursor = new NotificationSearchCursor(third.getCreatedAt(), third.getId());
		NotificationSearchQuery query = new NotificationSearchQuery(
			"requester-001", null, Set.of(),
			now.minusDays(1), now.plusDays(1),
			cursor, 20
		);

		List<Notification> found = notificationRepository.search(query, query.limit() + 1);

		assertThat(found).extracting(n -> n.getRequestId().value())
			.containsExactly("req-502", "req-501");
		assertThat(first.getId()).isLessThan(second.getId());
	}

	private Notification saveWith(String requestId, String requesterId, String receiver) {
		return save(NotificationFixture.builder()
			.withRequestId(requestId)
			.withRequesterId(requesterId)
			.withReceiver(receiver)
			.build());
	}

	private Notification save(NotificationRegisterRequest request) {
		return notificationRepository.save(Notification.register(request));
	}
}
