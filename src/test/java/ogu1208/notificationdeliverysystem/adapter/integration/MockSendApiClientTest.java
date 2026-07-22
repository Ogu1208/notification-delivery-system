package ogu1208.notificationdeliverysystem.adapter.integration;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;
import ogu1208.notificationdeliverysystem.support.fixture.NotificationFixture;

class MockSendApiClientTest {

	@Test
	@DisplayName("Mock Send API로 알림 발송 요청을 보낸다")
	void sendNotificationToMockSendApi() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		MockSendApiClient client = new MockSendApiClient(
			restClientBuilder.baseUrl("http://localhost:8081").build()
		);
		Notification notification = createSmsNotification();

		server.expect(once(), requestTo("http://localhost:8081/mock/send"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.requestId").value("req-001"))
			.andExpect(jsonPath("$.channelType").value("SMS"))
			.andExpect(jsonPath("$.receiver").value("01012345678"))
			.andExpect(jsonPath("$.message").value("hello"))
			.andExpect(jsonPath("$.metadata.notificationId").value(1))
			.andRespond(withSuccess("""
				{
				  "result": "SUCCESS",
				  "requestId": "req-001",
				  "channelType": "SMS",
				  "processedAt": "2026-07-22T00:00:00Z",
				  "latencyMs": 120
				}
				""", MediaType.APPLICATION_JSON));

		client.send(notification, NotificationChannel.SMS);

		server.verify();
	}

	private Notification createSmsNotification() {
		Notification notification = Notification.register(NotificationFixture.builder()
			.withReceiver("01012345678")
			.withChannel(NotificationChannel.SMS)
			.build());
		ReflectionTestUtils.setField(notification, "id", 1L);
		return notification;
	}
}
