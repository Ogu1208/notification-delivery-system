package ogu1208.notificationdeliverysystem.adapter.integration;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import ogu1208.notificationdeliverysystem.application.notification.required.DispatchLock;
import ogu1208.notificationdeliverysystem.application.notification.required.DispatchLockManager;

@Component
@ConditionalOnProperty(name = "notification.dispatch-lock.type", havingValue = "dummy", matchIfMissing = true)
public class DummyDispatchLockManager implements DispatchLockManager {

	private static final DispatchLock DUMMY_LOCK = () -> {
	};

	@Override
	public Optional<DispatchLock> tryAcquire(Long notificationId) {
		return Optional.of(DUMMY_LOCK);
	}
}
