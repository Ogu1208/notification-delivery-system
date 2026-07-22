package ogu1208.notificationdeliverysystem.application.notification.required;

import java.util.Optional;

public interface DispatchLockManager {

	Optional<DispatchLock> tryAcquire(Long notificationId);
}
