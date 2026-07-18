package ogu1208.notificationdeliverysystem.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture.createRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationRegister;
import ogu1208.notificationdeliverysystem.application.notification.required.ExternalNotificationSender;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventNotifier;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxAggregateType;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEventStatus;
import ogu1208.notificationdeliverysystem.domain.notification.RequestId;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

class NotificationRegisterTest {

	private final FakeNotificationRepository repository = new FakeNotificationRepository();
	private final StubExternalNotificationSender externalSender = new StubExternalNotificationSender();
	private final SpyOutboxEventRepository outboxEventRepository = new SpyOutboxEventRepository();
	private final SpyOutboxEventNotifier outboxEventNotifier = new SpyOutboxEventNotifier();
	private final NotificationRegister notificationRegister = new NotificationModifyService(
		repository,
		externalSender,
		outboxEventRepository,
		outboxEventNotifier
	);

	@Test
	@DisplayName("알림 등록 요청을 저장하고 Outbox 이벤트를 저장한다")
	void registerSavesNotificationAndOutboxEvent() {
		NotificationRegisterRequest request = createRequest();

		Notification notification = notificationRegister.register(request);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(notification.getRequestId()).isEqualTo(new RequestId("req-001"));
		assertThat(repository.findByRequestId("req-001")).containsSame(notification);
		assertThat(outboxEventRepository.savedEvent.getAggregateType()).isEqualTo(OutboxAggregateType.NOTIFICATION);
		assertThat(outboxEventRepository.savedEvent.getAggregateId()).isEqualTo(notification.getId());
		assertThat(outboxEventRepository.saveCount).isEqualTo(1);
		assertThat(outboxEventNotifier.notifiedNotificationId).isEqualTo(notification.getId());
		assertThat(outboxEventNotifier.notifyCount).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 요청 ID가 이미 있으면 기존 알림을 반환하고 다시 발행하지 않는다")
	void registerReturnsExistingNotificationWhenRequestIdAlreadyExists() {
		Notification existing = Notification.register(createRequest());
		repository.save(existing);
		NotificationRegisterRequest request = NotificationFixture.builder()
			.withReceiver("other@example.com")
			.withChannel(NotificationChannel.SMS)
			.withTitle("ignored")
			.withContent("ignored")
			.build();

		Notification notification = notificationRegister.register(request);

		assertThat(notification).isSameAs(existing);
		assertThat(repository.saveCount).isEqualTo(1);
		assertThat(outboxEventRepository.saveCount).isZero();
		assertThat(outboxEventNotifier.notifyCount).isZero();
	}

	private static class FakeNotificationRepository implements NotificationRepository {

		private final Map<String, Notification> notifications = new LinkedHashMap<>();
		private long idSequence = 1L;
		private int saveCount;

		@Override
		public Notification save(Notification notification) {
			if (notification.getId() == null) {
				ReflectionTestUtils.setField(notification, "id", idSequence++);
			}
			notifications.put(notification.getRequestId().value(), notification);
			saveCount++;
			return notification;
		}

		@Override
		public Optional<Notification> findByRequestId(String requestId) {
			return Optional.ofNullable(notifications.get(requestId));
		}

		@Override
		public Optional<Notification> findById(Long id) {
			return Optional.empty();
		}
	}

	private static class StubExternalNotificationSender implements ExternalNotificationSender {

		@Override
		public void send(Notification notification) {
		}
	}

	private static class SpyOutboxEventRepository implements OutboxEventRepository {

		private OutboxEvent savedEvent;
		private int saveCount;

		@Override
		public OutboxEvent save(OutboxEvent outboxEvent) {
			savedEvent = outboxEvent;
			saveCount++;
			return outboxEvent;
		}

		@Override
		public List<OutboxEvent> findByStatusOrderByIdAsc(OutboxEventStatus status, Pageable pageable) {
			return List.of();
		}

		@Override
		public Optional<OutboxEvent> findByAggregateTypeAndAggregateIdAndStatus(
			OutboxAggregateType aggregateType,
			Long aggregateId,
			OutboxEventStatus status
		) {
			return Optional.empty();
		}
	}

	private static class SpyOutboxEventNotifier implements OutboxEventNotifier {

		private Long notifiedNotificationId;
		private int notifyCount;

		@Override
		public void notifyOutboxSaved(Long notificationId) {
			notifiedNotificationId = notificationId;
			notifyCount++;
		}
	}
}
