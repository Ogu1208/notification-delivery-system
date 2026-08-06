package ogu1208.notificationdeliverysystem.adapter.webapi;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.adapter.webapi.dto.NotificationDetailResponse;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationAdmin;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;

/**
 * 관리자 운영용 API. 일반 사용자 API와 경로/권한을 분리하기 위해 별도 컨트롤러로 둔다.
 */
@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
public class NotificationAdminApi {

	private final NotificationAdmin notificationAdmin;

	@PostMapping("/{id}/retry")
	public NotificationDetailResponse resurrectForRetry(@PathVariable Long id) {
		Notification notification = notificationAdmin.resurrectForRetry(id);
		return NotificationDetailResponse.from(notification);
	}
}
