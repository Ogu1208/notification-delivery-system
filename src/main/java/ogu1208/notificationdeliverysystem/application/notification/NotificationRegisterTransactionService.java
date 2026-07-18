package ogu1208.notificationdeliverysystem.application.notification;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventNotifier;
import ogu1208.notificationdeliverysystem.application.notification.required.OutboxEventRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;
import ogu1208.notificationdeliverysystem.domain.outbox.OutboxEvent;

@Service
@RequiredArgsConstructor
public class NotificationRegisterTransactionService {

	private final NotificationRepository notificationRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final OutboxEventNotifier outboxEventNotifier;

	@Transactional(readOnly = true)
	public Optional<Notification> findByRequestId(String requestId) {
		return notificationRepository.findByRequestId(requestId);
	}

	/**
	 * 신규 알림과 Outbox 이벤트를 같은 DB 트랜잭션에 저장
	 *
	 * <p>requestId unique 제약 위반이 발생하면 이 트랜잭션은 롤백된다.
	 * 기존 알림 복구 조회는 호출자가 별도 트랜잭션으로 다시 수행한다.</p>
	 */
	@Transactional
	public Notification registerNewNotification(NotificationRegisterRequest request) {
		Notification notification = Notification.register(request);
		Notification savedNotification = notificationRepository.save(notification);

		outboxEventRepository.save(OutboxEvent.singleNotificationSend(savedNotification.getId()));
		outboxEventNotifier.notifyOutboxSaved(savedNotification.getId());

		return savedNotification;
	}
}
