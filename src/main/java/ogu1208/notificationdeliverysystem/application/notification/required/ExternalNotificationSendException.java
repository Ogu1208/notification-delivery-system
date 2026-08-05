package ogu1208.notificationdeliverysystem.application.notification.required;

import lombok.Getter;

@Getter
public class ExternalNotificationSendException extends RuntimeException {

	private final boolean retryable;
	private final String errorCode;
	private final Integer externalStatusCode;
	private final Long elapsedTimeMs;

	private ExternalNotificationSendException(
		String message,
		boolean retryable,
		String errorCode,
		Integer externalStatusCode,
		Long elapsedTimeMs,
		Throwable cause
	) {
		super(message, cause);
		this.retryable = retryable;
		this.errorCode = errorCode;
		this.externalStatusCode = externalStatusCode;
		this.elapsedTimeMs = elapsedTimeMs;
	}

	public static ExternalNotificationSendException retryable(
		String message,
		String errorCode,
		Integer externalStatusCode,
		Throwable cause
	) {
		return retryable(message, errorCode, externalStatusCode, null, cause);
	}

	public static ExternalNotificationSendException retryable(
		String message,
		String errorCode,
		Integer externalStatusCode,
		Long elapsedTimeMs,
		Throwable cause
	) {
		return new ExternalNotificationSendException(message, true, errorCode, externalStatusCode, elapsedTimeMs, cause);
	}

	public static ExternalNotificationSendException nonRetryable(
		String message,
		String errorCode,
		Integer externalStatusCode,
		Throwable cause
	) {
		return nonRetryable(message, errorCode, externalStatusCode, null, cause);
	}

	public static ExternalNotificationSendException nonRetryable(
		String message,
		String errorCode,
		Integer externalStatusCode,
		Long elapsedTimeMs,
		Throwable cause
	) {
		return new ExternalNotificationSendException(message, false, errorCode, externalStatusCode, elapsedTimeMs, cause);
	}
}
