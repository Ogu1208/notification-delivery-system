package ogu1208.notificationdeliverysystem.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture.createRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;

import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

@DataJpaTest
@Import(JpaAuditingConfig.class)
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
		assertThat(notificationRepository.findByRequestIdValue("req-001")).contains(savedNotification);
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
}
