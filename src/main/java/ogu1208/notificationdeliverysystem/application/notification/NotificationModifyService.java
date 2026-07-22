package ogu1208.notificationdeliverysystem.application.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationRegister;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationSender;
import ogu1208.notificationdeliverysystem.application.notification.required.DispatchLock;
import ogu1208.notificationdeliverysystem.application.notification.required.DispatchLockManager;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;

@Service
@Validated
@RequiredArgsConstructor
public class NotificationModifyService implements NotificationRegister, NotificationSender {

	private static final Logger log = LoggerFactory.getLogger(NotificationModifyService.class);

	private final NotificationRegisterTransactionService registerTransactionService;
	private final NotificationDispatchService dispatchService;
	private final DispatchLockManager dispatchLockManager;

	@Override
	public Notification register(NotificationRegisterRequest request) {
		return registerTransactionService.findByRequestId(request.requestId())
			.orElseGet(() -> registerNewOrFindExisting(request));
	}

	/*
	 * 동시 중복 요청 방어
	 *
	 * 먼저 조회했을 때 없더라도, 다른 요청이 먼저 insert 하면 unique 제약 위반이 발생할 수 있다.
	 * 제약 위반이 난 트랜잭션은 rollback-only 또는 Hibernate 세션 실패 상태가 될 수 있으므로
	 * 저장 트랜잭션이 롤백된 뒤, 분리된 조회 트랜잭션으로 기존 알림을 다시 찾아 멱등 응답을 맞춘다.
	 */
	private Notification registerNewOrFindExisting(NotificationRegisterRequest request) {
		try {
			return registerTransactionService.registerNewNotification(request);
		} catch (DataIntegrityViolationException e) {
			return registerTransactionService.findByRequestId(request.requestId())
				.orElseThrow(() -> e);
		}
	}

	/**
	 * Kafka at-least-once 재전달 대비
	 *
	 * 같은 메시지가 다시 와도 이미 완료된 알림은 외부 발송을 반복하지 않는다.
	 * 동시 발송 방지는 이후 Redis 분산락 단계에서 보강한다.
	 */
	@Override
	public void send(Long notificationId) {
		if (dispatchService.isCompleted(notificationId)) {
			log.info("완료 상태 알림 발송 건너뜀. notificationId={}", notificationId);
			return;
		}

		var lock = dispatchLockManager.tryAcquire(notificationId);
		if (lock.isEmpty()) {
			log.info("다른 Consumer가 처리 중인 알림 발송 건너뜀. notificationId={}", notificationId);
			return;
		}

		try (DispatchLock ignored = lock.get()) {
			// try-with-resources가 발송 처리 종료 후 DispatchLock.close()를 호출해 락 해제를 보장한다.
			dispatchService.send(notificationId);
		}
	}
}
