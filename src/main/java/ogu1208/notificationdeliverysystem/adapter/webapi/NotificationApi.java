package ogu1208.notificationdeliverysystem.adapter.webapi;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.adapter.webapi.dto.NotificationRegisterResponse;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationRegister;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;

@RestController
@RequiredArgsConstructor
public class NotificationApi {

	private final NotificationRegister notificationRegister;

	@PostMapping("/notifications")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public NotificationRegisterResponse register(@Valid @RequestBody NotificationRegisterRequest request) {
		Notification notification = notificationRegister.register(request);
		return NotificationRegisterResponse.from(notification);
	}
}
