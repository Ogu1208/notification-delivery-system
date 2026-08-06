package ogu1208.notificationdeliverysystem.adapter.webapi.dto;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;

/**
 * 알림 조회 API의 query parameter 묶음.
 *
 * <p>Spring이 {@code ?requesterId=...&limit=20} 같은 query string을 자동으로 이 record에 바인딩한다.
 * URL 형태는 개별 {@code @RequestParam}과 동일하다.
 */
public record NotificationSearchParams(
	String requesterId,
	String receiver,
	List<NotificationStatus> status,
	List<NotificationChannel> channel,
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
	String cursor,
	@Min(1) @Max(100) Integer limit
) {
}
