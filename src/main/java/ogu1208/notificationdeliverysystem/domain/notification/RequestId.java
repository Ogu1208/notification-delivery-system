package ogu1208.notificationdeliverysystem.domain.notification;

import static org.springframework.util.Assert.hasText;

import jakarta.persistence.Embeddable;

@Embeddable
public record RequestId(String value) {

	public RequestId {
		hasText(value, "요청 ID는 필수입니다");
	}
}
