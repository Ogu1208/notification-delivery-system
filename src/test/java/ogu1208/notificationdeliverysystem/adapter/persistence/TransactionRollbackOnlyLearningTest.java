package ogu1208.notificationdeliverysystem.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.hibernate.AssertionFailure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

@SpringBootTest
@Import(TransactionRollbackOnlyLearningTest.DuplicateRequestLearningService.class)
class TransactionRollbackOnlyLearningTest {

	@Autowired
	private DuplicateRequestLearningService learningService;

	@Test
	@DisplayName("같은 트랜잭션에서 unique 위반을 잡고 조회하면 Hibernate 세션이 실패 상태가 된다")
	void recoverDuplicateInSameTransactionFails() {
		String requestId = "rollback-only-same";
		learningService.saveOriginal(requestId);

		// 중복 저장 실패 후 같은 영속성 컨텍스트로 조회를 이어가면 Hibernate가 세션을 안전하지 않은 상태로 본다.
		assertThatThrownBy(() -> learningService.tryDuplicateAndFindInSameTransaction(requestId))
			.isInstanceOf(AssertionFailure.class)
			.hasMessageContaining("session is flushed after an exception occurs");
	}

	@Test
	@DisplayName("unique 위반 트랜잭션과 기존 알림 조회 트랜잭션을 분리하면 복구 조회가 가능하다")
	void recoverDuplicateWithSeparatedTransaction() {
		String requestId = "rollback-only-separated";
		learningService.saveOriginal(requestId);

		// 실패한 저장 트랜잭션을 롤백한 뒤 새 트랜잭션에서 기존 알림을 조회하는 복구 흐름이다.
		Optional<Notification> notification = learningService.tryDuplicateAndFindInSeparatedTransaction(requestId);

		assertThat(notification).isPresent();
	}

	@Service
	@RequiredArgsConstructor
	static class DuplicateRequestLearningService {

		private final NotificationRepository notificationRepository;
		private final PlatformTransactionManager transactionManager;

		@Transactional
		void saveOriginal(String requestId) {
			notificationRepository.save(Notification.register(request(requestId)));
		}

		@Transactional
		Optional<Notification> tryDuplicateAndFindInSameTransaction(String requestId) {
			try {
				notificationRepository.save(Notification.register(request(requestId)));
			} catch (DataIntegrityViolationException exception) {
				return notificationRepository.findByRequestId(requestId);
			}
			return Optional.empty();
		}

		Optional<Notification> tryDuplicateAndFindInSeparatedTransaction(String requestId) {
			try {
				// TransactionTemplate은 테스트에서 트랜잭션 경계를 코드 블록 단위로 직접 보여주기 위해 사용한다.
				newTransaction().executeWithoutResult(status ->
					notificationRepository.save(Notification.register(request(requestId)))
				);
			} catch (DataIntegrityViolationException exception) {
				return newTransaction().execute(status -> notificationRepository.findByRequestId(requestId));
			}
			return Optional.empty();
		}

		private TransactionTemplate newTransaction() {
			return new TransactionTemplate(transactionManager);
		}

		private NotificationRegisterRequest request(String requestId) {
			return NotificationFixture.builder()
				.withRequestId(requestId)
				.build();
		}
	}
}
