package ogu1208.notificationdeliverysystem.application.notification.required;

/**
 * try-with-resources로 발송 락 해제를 보장하기 위한 락 핸들.
 *
 * <p>AutoCloseable은 Java 표준 인터페이스다.
 * try 블록이 정상 종료되거나 예외로 종료되어도 close()가 호출된다.</p>
 */
public interface DispatchLock extends AutoCloseable {

	@Override
	void close();
}
