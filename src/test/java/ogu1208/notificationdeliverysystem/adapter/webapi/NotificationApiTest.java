package ogu1208.notificationdeliverysystem.adapter.webapi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture.createNotification;
import static ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture.createRequest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import ogu1208.notificationdeliverysystem.application.notification.NotificationNotFoundException;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationFinder;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationRegister;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchCursor;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchResult;
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

	@MockitoBean
	private NotificationFinder notificationFinder;

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

	@Test
	@DisplayName("GET /notifications는 검색 결과와 nextCursor를 반환한다")
	void searchReturnsItemsAndCursor() throws Exception {
		Notification notification = createNotification(1L);
		ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.of(2026, 8, 1, 12, 34, 56));
		NotificationSearchCursor cursor = new NotificationSearchCursor(
			LocalDateTime.of(2026, 8, 1, 12, 34, 56),
			1L
		);
		when(notificationFinder.search(any()))
			.thenReturn(new NotificationSearchResult(List.of(notification), cursor));

		mockMvc.perform(get("/notifications")
				.param("requesterId", "requester-001")
				.param("limit", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].id").value(1))
			.andExpect(jsonPath("$.items[0].requesterId").value("requester-001"))
			.andExpect(jsonPath("$.items[0].receiver").value("user@example.com"))
			.andExpect(jsonPath("$.nextCursor").value(cursor.encode()));
	}

	@Test
	@DisplayName("requesterId와 receiver가 모두 없으면 400 ProblemDetail 응답을 반환한다")
	void searchRejectsWhenNoKeyProvided() throws Exception {
		mockMvc.perform(get("/notifications"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.detail").value("요청자 또는 수신자 중 최소 하나는 지정해야 합니다"))
			.andExpect(jsonPath("$.exception").value("IllegalArgumentException"));
	}

	@Test
	@DisplayName("GET /notifications/{id}는 단건 상세를 반환한다")
	void detailReturnsSingleNotification() throws Exception {
		Notification notification = createNotification(1L);
		when(notificationFinder.findById(1L)).thenReturn(notification);

		mockMvc.perform(get("/notifications/1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(1))
			.andExpect(jsonPath("$.requestId").value("req-001"))
			.andExpect(jsonPath("$.content").value("hello"));
	}

	@Test
	@DisplayName("존재하지 않는 알림 상세 조회는 404 ProblemDetail 응답을 반환한다")
	void detailReturns404WhenMissing() throws Exception {
		when(notificationFinder.findById(eq(999L)))
			.thenThrow(new NotificationNotFoundException(999L));

		mockMvc.perform(get("/notifications/999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.detail").value("알림을 찾을 수 없습니다. notificationId=999"))
			.andExpect(jsonPath("$.exception").value("NotificationNotFoundException"));
	}

	@Test
	@DisplayName("GET /notifications/by-request/{requestId}는 단건 상세를 반환한다")
	void detailByRequestIdReturnsSingleNotification() throws Exception {
		Notification notification = createNotification(1L);
		when(notificationFinder.findByRequestId("req-001")).thenReturn(notification);

		mockMvc.perform(get("/notifications/by-request/req-001"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(1))
			.andExpect(jsonPath("$.requestId").value("req-001"));
	}

	@Test
	@DisplayName("존재하지 않는 requestId 상세 조회는 404 ProblemDetail 응답을 반환한다")
	void detailByRequestIdReturns404WhenMissing() throws Exception {
		when(notificationFinder.findByRequestId("req-missing"))
			.thenThrow(NotificationNotFoundException.byRequestId("req-missing"));

		mockMvc.perform(get("/notifications/by-request/req-missing"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.detail").value("알림을 찾을 수 없습니다. requestId=req-missing"))
			.andExpect(jsonPath("$.exception").value("NotificationNotFoundException"));
	}
}
