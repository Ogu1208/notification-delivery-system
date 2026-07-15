package ogu1208.notificationdeliverysystem.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;

@DataJpaTest
class NotificationRepositoryTest {

	@Autowired
	private NotificationJpaRepository notificationRepository;

	@Test
	void saveAndFindByRequestId() {
		Notification notification = Notification.register(
			"req-001",
			"user@example.com",
			NotificationChannel.EMAIL,
			"welcome",
			"hello"
		);

		Notification savedNotification = notificationRepository.save(notification);

		assertThat(savedNotification.getId()).isNotNull();
		assertThat(savedNotification.getCreatedAt()).isNotNull();
		assertThat(savedNotification.getUpdatedAt()).isNotNull();
		assertThat(notificationRepository.findByRequestId("req-001")).contains(savedNotification);
	}

	@Test
	void requestIdMustBeUnique() {
		notificationRepository.save(Notification.register(
			"req-001",
			"user@example.com",
			NotificationChannel.EMAIL,
			"welcome",
			"hello"
		));
		notificationRepository.flush();

		assertThatThrownBy(() -> notificationRepository.saveAndFlush(Notification.register(
			"req-001",
			"other@example.com",
			NotificationChannel.SMS,
			"retry",
			"hello"
		)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
}
