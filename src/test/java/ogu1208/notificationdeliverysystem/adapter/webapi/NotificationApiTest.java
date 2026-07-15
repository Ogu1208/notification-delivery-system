package ogu1208.notificationdeliverysystem.adapter.webapi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationRegister;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;

@WebMvcTest(NotificationApi.class)
class NotificationApiTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private NotificationRegister notificationRegister;

	@Test
	void registerNotificationReturnsAcceptedResponse() throws Exception {
		Notification notification = Notification.register(
			"req-001",
			"user@example.com",
			NotificationChannel.EMAIL,
			"welcome",
			"hello"
		);
		ReflectionTestUtils.setField(notification, "id", 1L);
		when(notificationRegister.register(any())).thenReturn(notification);

		mockMvc.perform(post("/notifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "requestId": "req-001",
					  "receiver": "user@example.com",
					  "channel": "EMAIL",
					  "title": "welcome",
					  "content": "hello"
					}
					"""))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.notificationId").value(1))
			.andExpect(jsonPath("$.requestId").value("req-001"))
			.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void registerNotificationRejectsBlankRequestId() throws Exception {
		mockMvc.perform(post("/notifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "requestId": " ",
					  "receiver": "user@example.com",
					  "channel": "EMAIL",
					  "title": "welcome",
					  "content": "hello"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.detail").value("요청 ID는 필수입니다"))
			.andExpect(jsonPath("$.timestamp").exists())
			.andExpect(jsonPath("$.exception").value("MethodArgumentNotValidException"));
	}

	@Test
	void registerNotificationConvertsIllegalArgumentExceptionToBadRequestProblemDetail() throws Exception {
		when(notificationRegister.register(any()))
			.thenThrow(new IllegalArgumentException("수신자는 필수입니다"));

		mockMvc.perform(post("/notifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "requestId": "req-001",
					  "receiver": "user@example.com",
					  "channel": "EMAIL",
					  "title": "welcome",
					  "content": "hello"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.detail").value("수신자는 필수입니다"))
			.andExpect(jsonPath("$.timestamp").exists())
			.andExpect(jsonPath("$.exception").value("IllegalArgumentException"));
	}
}
