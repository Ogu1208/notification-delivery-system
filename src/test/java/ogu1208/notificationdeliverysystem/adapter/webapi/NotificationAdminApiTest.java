package ogu1208.notificationdeliverysystem.adapter.webapi;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ogu1208.notificationdeliverysystem.application.notification.NotificationNotFoundException;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationAdmin;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

@WebMvcTest(NotificationAdminApi.class)
class NotificationAdminApiTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private NotificationAdmin notificationAdmin;

	@Test
	@DisplayName("POST /admin/notifications/{id}/retry는 재시도된 알림 상세를 반환한다")
	void resurrectReturnsDetail() throws Exception {
		Notification notification = NotificationFixture.createNotification(1L);
		when(notificationAdmin.resurrectForRetry(1L)).thenReturn(notification);

		mockMvc.perform(post("/admin/notifications/1/retry"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(1))
			.andExpect(jsonPath("$.requestId").value("req-001"));
	}

	@Test
	@DisplayName("존재하지 않는 알림 재시도는 404 ProblemDetail 응답을 반환한다")
	void resurrectReturns404WhenMissing() throws Exception {
		when(notificationAdmin.resurrectForRetry(999L))
			.thenThrow(new NotificationNotFoundException(999L));

		mockMvc.perform(post("/admin/notifications/999/retry"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.detail").value("알림을 찾을 수 없습니다. notificationId=999"));
	}

	@Test
	@DisplayName("DEAD가 아닌 알림 재시도는 400 ProblemDetail 응답을 반환한다")
	void resurrectReturns400WhenNotDead() throws Exception {
		when(notificationAdmin.resurrectForRetry(1L))
			.thenThrow(new IllegalStateException("DEAD 상태의 알림만 수동 재시도할 수 있습니다"));

		mockMvc.perform(post("/admin/notifications/1/retry"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.detail").value("DEAD 상태의 알림만 수동 재시도할 수 있습니다"));
	}
}
