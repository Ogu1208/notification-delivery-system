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
	@DisplayName("요청자 기준 조회는 다른 요청자의 알림을 제외한다")
	void searchByRequesterExcludesOtherRequesters() {
		saveWith("req-201", "requester-A", "user1@example.com");
		saveWith("req-202", "requester-A", "user2@example.com");
		saveWith("req-203", "requester-B", "user3@example.com");
		entityManager.flush();
		entityManager.clear();

		LocalDateTime now = LocalDateTime.now();
		NotificationSearchQuery query = new NotificationSearchQuery(
			"requester-A", null, Set.of(), Set.of(),
			now.minusDays(1), now.plusDays(1),
			null, 20
		);

		List<Notification> found = notificationRepository.search(query, query.limit() + 1);

		assertThat(found).extracting(n -> n.getRequestId().value())
			.containsExactlyInAnyOrder("req-201", "req-202");
	}

	@Test
	@DisplayName("요청자 기준 조회는 from/to 범위 밖의 알림을 제외한다")
	void searchByRequesterExcludesOutOfDateRange() {
		LocalDateTime now = LocalDateTime.now();
		Notification inRange1 = saveWith("req-211", "requester-A", "user1@example.com");
		Notification inRange2 = saveWith("req-212", "requester-A", "user2@example.com");
		Notification beforeRange = saveWith("req-213", "requester-A", "user3@example.com");
		Notification afterRange = saveWith("req-214", "requester-A", "user4@example.com");
		overrideCreatedAt(beforeRange.getId(), now.minusDays(30));
		overrideCreatedAt(afterRange.getId(), now.plusDays(30));
		entityManager.flush();
		entityManager.clear();

		NotificationSearchQuery query = new NotificationSearchQuery(
			"requester-A", null, Set.of(), Set.of(),
			now.minusDays(1), now.plusDays(1),
			null, 20
		);

		List<Notification> found = notificationRepository.search(query, query.limit() + 1);

		assertThat(found).extracting(n -> n.getRequestId().value())
			.containsExactlyInAnyOrder("req-211", "req-212");
	}

	@Test
	@DisplayName("요청자 기준 조회는 createdAt DESC로 최신순 정렬한다")
	void searchByRequesterOrdersByCreatedAtDesc() {
		LocalDateTime now = LocalDateTime.now();
		Notification older = saveWith("req-221", "requester-A", "user1@example.com");
		Notification newer = saveWith("req-222", "requester-A", "user2@example.com");
		overrideCreatedAt(older.getId(), now.minusHours(3));
		overrideCreatedAt(newer.getId(), now.minusHours(1));
		entityManager.flush();
		entityManager.clear();

		NotificationSearchQuery query = new NotificationSearchQuery(
			"requester-A", null, Set.of(), Set.of(),
			now.minusDays(1), now.plusDays(1),
			null, 20
		);

		List<Notification> found = notificationRepository.search(query, query.limit() + 1);

		assertThat(found).extracting(n -> n.getRequestId().value())
			.containsExactly("req-222", "req-221");
	}

	@Test
	@DisplayName("요청자와 수신자를 함께 지정하면 두 조건을 모두 만족하는 알림만 반환한다")
	void searchByRequesterAndReceiverCombined() {
		saveWith("req-231", "requester-A", "target@example.com");
		saveWith("req-232", "requester-A", "other@example.com");
		saveWith("req-233", "requester-B", "target@example.com");
		saveWith("req-234", "requester-B", "other@example.com");
		entityManager.flush();
		entityManager.clear();

		LocalDateTime now = LocalDateTime.now();
		NotificationSearchQuery query = new NotificationSearchQuery(
			"requester-A", "target@example.com", Set.of(), Set.of(),
			now.minusDays(1), now.plusDays(1),
			null, 20
		);

		List<Notification> found = notificationRepository.search(query, query.limit() + 1);

		assertThat(found).extracting(n -> n.getRequestId().value())
			.containsExactly("req-231");
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
			null, "target@example.com", Set.of(), Set.of(),
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
			"requester-001", null, Set.of(NotificationStatus.SENT), Set.of(),
			now.minusDays(1), now.plusDays(1),
			null, 20
		);

		List<Notification> found = notificationRepository.search(query, query.limit() + 1);

		assertThat(found).hasSize(1);
		assertThat(found.get(0).getRequestId().value()).isEqualTo("req-402");
		assertThat(pending).isNotNull(); // used only to keep visible baseline row
	}

	@Test
	@DisplayName("채널 필터를 주면 해당 채널의 알림만 반환한다")
	void searchWithChannelFilter() {
		save(NotificationFixture.builder().withRequestId("req-411").withChannel(NotificationChannel.EMAIL).build());
		save(NotificationFixture.builder().withRequestId("req-412").withChannel(NotificationChannel.SMS).build());
		save(NotificationFixture.builder().withRequestId("req-413").withChannel(NotificationChannel.KAKAO).build());
		entityManager.flush();
		entityManager.clear();

		LocalDateTime now = LocalDateTime.now();
		NotificationSearchQuery query = new NotificationSearchQuery(
			"requester-001", null, Set.of(),
			Set.of(NotificationChannel.EMAIL, NotificationChannel.SMS),
			now.minusDays(1), now.plusDays(1),
			null, 20
		);

		List<Notification> found = notificationRepository.search(query, query.limit() + 1);

		assertThat(found).extracting(n -> n.getRequestId().value())
			.containsExactlyInAnyOrder("req-411", "req-412");
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
		LocalDateTime from = now.minusDays(1);
		LocalDateTime to = now.plusDays(1);
		NotificationSearchCursor cursor = new NotificationSearchCursor(
			third.getCreatedAt(), third.getId(), from, to
		);
		NotificationSearchQuery query = new NotificationSearchQuery(
			"requester-001", null, Set.of(), Set.of(),
			from, to,
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

	// JPA Auditing으로 자동 세팅되는 createdAt을 테스트에서 강제로 조작.
	// 기간 필터 / 정렬 검증을 위해 과거·미래 시각으로 밀어 두는 용도.
	private void overrideCreatedAt(Long id, LocalDateTime createdAt) {
		entityManager.getEntityManager()
			.createNativeQuery("UPDATE notification SET created_at = ? WHERE id = ?")
			.setParameter(1, createdAt)
			.setParameter(2, id)
			.executeUpdate();
	}
}
