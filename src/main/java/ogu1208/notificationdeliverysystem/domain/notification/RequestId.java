package ogu1208.notificationdeliverysystem.domain.notification;

public record RequestId(String value) {

	public RequestId {
		if (isBlank(value)) {
			throw new IllegalArgumentException("요청 ID는 필수입니다");
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
