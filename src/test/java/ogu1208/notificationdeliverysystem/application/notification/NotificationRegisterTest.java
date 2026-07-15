package ogu1208.notificationdeliverysystem.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static ogu1208.notificationdeliverysystem.support.fixture.NotificationRegisterRequestFixture.create;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationRegister;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationMessagePublisher;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;
import ogu1208.notificationdeliverysystem.domain.notification.RequestId;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationRegisterRequestFixture;

class NotificationRegisterTest {

	private final FakeNotificationRepository repository = new FakeNotificationRepository();
	private final SpyNotificationMessagePublisher publisher = new SpyNotificationMessagePublisher();
	private final NotificationRegister notificationRegister = new NotificationModifyService(repository, publisher);

	@Test
	void registerSavesNotificationAndPublishesMessage() {
		NotificationRegisterRequest request = create();

		Notification notification = notificationRegister.register(request);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(notification.getRequestId()).isEqualTo(new RequestId("req-001"));
		assertThat(repository.findByRequestIdValue("req-001")).containsSame(notification);
		assertThat(publisher.publishedNotification).isSameAs(notification);
		assertThat(publisher.publishCount).isEqualTo(1);
	}

	@Test
	void registerReturnsExistingNotificationWhenRequestIdAlreadyExists() {
		Notification existing = Notification.register(create());
		repository.save(existing);
		NotificationRegisterRequest request = NotificationRegisterRequestFixture.builder()
			.withReceiver("other@example.com")
			.withChannel(NotificationChannel.SMS)
			.withTitle("ignored")
			.withContent("ignored")
			.build();

		Notification notification = notificationRegister.register(request);

		assertThat(notification).isSameAs(existing);
		assertThat(repository.saveCount).isEqualTo(1);
		assertThat(publisher.publishCount).isZero();
	}

	private static class FakeNotificationRepository implements NotificationRepository {

		private final Map<String, Notification> notifications = new LinkedHashMap<>();
		private int saveCount;

		@Override
		public Notification save(Notification notification) {
			notifications.put(notification.getRequestId().value(), notification);
			saveCount++;
			return notification;
		}

		@Override
		public Optional<Notification> findByRequestIdValue(String requestId) {
			return Optional.ofNullable(notifications.get(requestId));
		}
	}

	private static class SpyNotificationMessagePublisher implements NotificationMessagePublisher {

		private Notification publishedNotification;
		private int publishCount;

		@Override
		public void publish(Notification notification) {
			publishedNotification = notification;
			publishCount++;
		}
	}
}
