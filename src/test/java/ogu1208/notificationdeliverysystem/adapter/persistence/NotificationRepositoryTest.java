package ogu1208.notificationdeliverysystem.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ogu1208.notificationdeliverysystem.support.fixture.NotificationRegisterRequestFixture.create;

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
import ogu1208.notificationdeliverysystem.support.fixture.NotificationRegisterRequestFixture;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class NotificationRepositoryTest {

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void saveAndFindByRequestId() {
		Notification notification = Notification.register(create());

		Notification savedNotification = notificationRepository.save(notification);

		assertThat(savedNotification.getId()).isNotNull();
		assertThat(savedNotification.getCreatedAt()).isNotNull();
		assertThat(savedNotification.getUpdatedAt()).isNotNull();
		assertThat(notificationRepository.findByRequestIdValue("req-001")).contains(savedNotification);
	}

	@Test
	void requestIdMustBeUnique() {
		notificationRepository.save(Notification.register(create()));
		entityManager.flush();

		NotificationRegisterRequest duplicateRequest = NotificationRegisterRequestFixture.builder()
			.withReceiver("other@example.com")
			.withChannel(NotificationChannel.SMS)
			.withTitle("retry")
			.build();

		assertThatThrownBy(() -> notificationRepository.save(Notification.register(duplicateRequest)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
}
