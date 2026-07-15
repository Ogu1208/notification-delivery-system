package ogu1208.notificationdeliverysystem.domain.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationRegisterRequest(
	@NotBlank(message = "요청 ID는 필수입니다") String requestId,
	@NotBlank(message = "수신자는 필수입니다") String receiver,
	@NotNull(message = "발송 채널은 필수입니다") NotificationChannel channel,
	@NotBlank(message = "제목은 필수입니다") String title,
	@NotBlank(message = "본문은 필수입니다") String content
) {
}
