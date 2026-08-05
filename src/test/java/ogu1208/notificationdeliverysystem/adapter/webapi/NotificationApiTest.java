package ogu1208.notificationdeliverysystem.adapter.webapi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture.createNotification;
import static ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture.createRequest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationRegister;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;

@WebMvcTest(NotificationApi.class)
class NotificationApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private NotificationRegister notificationRegister;

	@Test
	@DisplayName("알림 등록 요청을 provided port에 전달하고 접수 응답을 반환한다")
	void registerNotification() throws Exception {
		NotificationRegisterRequest request = createRequest();
		Notification notification = createNotification(1L);
		when(notificationRegister.register(any())).thenReturn(notification);

		mockMvc.perform(post("/notifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.notificationId").value(1))
			.andExpect(jsonPath("$.requestId").value("req-001"))
			.andExpect(jsonPath("$.status").value("PENDING"));

		verify(notificationRegister).register(request);
	}

	@Test
	@DisplayName("요청 ID가 비어 있으면 400 ProblemDetail 응답을 반환한다")
	void registerNotificationRejectsBlankRequestId() throws Exception {
		mockMvc.perform(post("/notifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "requestId": " ",
					  "requesterId": "requester-001",
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
	@DisplayName("유스케이스에서 잘못된 요청 예외가 발생하면 400 ProblemDetail 응답을 반환한다")
	void registerNotificationConvertsIllegalArgumentExceptionToBadRequestProblemDetail() throws Exception {
		when(notificationRegister.register(any()))
			.thenThrow(new IllegalArgumentException("수신자는 필수입니다"));

		mockMvc.perform(post("/notifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "requestId": "req-001",
					  "requesterId": "requester-001",
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
