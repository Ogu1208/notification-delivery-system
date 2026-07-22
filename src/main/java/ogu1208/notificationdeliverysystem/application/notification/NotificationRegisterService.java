package ogu1208.notificationdeliverysystem.application.notification;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationRegister;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;

@Service
@Validated
@RequiredArgsConstructor
public class NotificationRegisterService implements NotificationRegister {

	private final NotificationRegisterTransactionService registerTransactionService;

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
}
