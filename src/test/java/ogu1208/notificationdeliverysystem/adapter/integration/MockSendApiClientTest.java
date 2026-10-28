package ogu1208.notificationdeliverysystem.adapter.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ogu1208.notificationdeliverysystem.application.notification.required.ExternalNotificationSendException;
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

	@Test
	@DisplayName("Mock Send API 5xx 응답은 재시도 가능한 실패로 분류한다")
	void classify5xxAsRetryableFailure() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		MockSendApiClient client = new MockSendApiClient(
			restClientBuilder.baseUrl("http://localhost:8081").build()
		);
		Notification notification = createSmsNotification();

		server.expect(once(), requestTo("http://localhost:8081/mock/send"))
			.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
					{
					  "code": "MOCK_UNAVAILABLE",
					  "message": "mock api unavailable"
					}
					"""));

		assertThatThrownBy(() -> client.send(notification, NotificationChannel.SMS))
			.isInstanceOfSatisfying(ExternalNotificationSendException.class, exception -> {
				assertThat(exception.isRetryable()).isTrue();
				assertThat(exception.getErrorCode()).isEqualTo("MOCK_UNAVAILABLE");
				assertThat(exception.getExternalStatusCode()).isEqualTo(503);
			});

		server.verify();
	}

	@Test
	@DisplayName("Mock Send API 일반 4xx 응답은 재시도하지 않는 실패로 분류한다")
	void classify4xxAsNonRetryableFailure() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		MockSendApiClient client = new MockSendApiClient(
			restClientBuilder.baseUrl("http://localhost:8081").build()
		);
		Notification notification = createSmsNotification();

		server.expect(once(), requestTo("http://localhost:8081/mock/send"))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
					{
					  "code": "INVALID_RECEIVER",
					  "message": "invalid receiver"
					}
					"""));

		assertThatThrownBy(() -> client.send(notification, NotificationChannel.SMS))
			.isInstanceOfSatisfying(ExternalNotificationSendException.class, exception -> {
				assertThat(exception.isRetryable()).isFalse();
				assertThat(exception.getErrorCode()).isEqualTo("INVALID_RECEIVER");
				assertThat(exception.getExternalStatusCode()).isEqualTo(400);
			});

		server.verify();
	}

	@Test
	@DisplayName("Mock Send API timeout은 재시도 가능한 실패로 분류한다")
	void classifyTimeoutAsRetryableFailure() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		MockSendApiClient client = new MockSendApiClient(
			restClientBuilder.baseUrl("http://localhost:8081").build()
		);
		Notification notification = createSmsNotification();

		server.expect(once(), requestTo("http://localhost:8081/mock/send"))
			.andRespond(withException(new SocketTimeoutException("read timed out")));

		assertThatThrownBy(() -> client.send(notification, NotificationChannel.SMS))
			.isInstanceOfSatisfying(ExternalNotificationSendException.class, exception -> {
				assertThat(exception.isRetryable()).isTrue();
				assertThat(exception.getErrorCode()).isEqualTo("MOCK_SEND_TIMEOUT");
				assertThat(exception.getExternalStatusCode()).isNull();
			});

		server.verify();
	}

	@Test
	@DisplayName("Mock Send API 성공 응답의 requestId가 요청과 다르면 재시도하지 않는 실패로 분류한다")
	void classifyMismatchedResponseRequestIdAsNonRetryableFailure() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		MockSendApiClient client = new MockSendApiClient(
			restClientBuilder.baseUrl("http://localhost:8081").build()
		);
		Notification notification = createSmsNotification();

		server.expect(once(), requestTo("http://localhost:8081/mock/send"))
			.andExpect(jsonPath("$.requestId").value("req-001"))
			.andRespond(withSuccess("""
				{
				  "result": "SUCCESS",
				  "requestId": "req-999",
				  "channelType": "SMS",
				  "processedAt": "2026-07-22T00:00:00Z",
				  "latencyMs": 120
				}
				""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.send(notification, NotificationChannel.SMS))
			.isInstanceOfSatisfying(ExternalNotificationSendException.class, exception -> {
				assertThat(exception.isRetryable()).isFalse();
				assertThat(exception.getErrorCode()).isEqualTo("MOCK_REQUEST_ID_MISMATCH");
				assertThat(exception.getExternalStatusCode()).isEqualTo(200);
			});

		server.verify();
	}

	@Test
	@DisplayName("Mock Send API 성공 응답에 requestId가 없으면 재시도하지 않는 실패로 분류한다")
	void classifyMissingResponseRequestIdAsNonRetryableFailure() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		MockSendApiClient client = new MockSendApiClient(
			restClientBuilder.baseUrl("http://localhost:8081").build()
		);
		Notification notification = createSmsNotification();

		server.expect(once(), requestTo("http://localhost:8081/mock/send"))
			.andRespond(withSuccess("""
				{
				  "result": "SUCCESS",
				  "channelType": "SMS",
				  "processedAt": "2026-07-22T00:00:00Z",
				  "latencyMs": 120
				}
				""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.send(notification, NotificationChannel.SMS))
			.isInstanceOfSatisfying(ExternalNotificationSendException.class, exception -> {
				assertThat(exception.isRetryable()).isFalse();
				assertThat(exception.getErrorCode()).isEqualTo("MOCK_REQUEST_ID_MISMATCH");
			});

		server.verify();
	}

	@Test
	@DisplayName("Mock Send API 성공 응답의 requestId가 공백이면 재시도하지 않는 실패로 분류한다")
	void classifyBlankResponseRequestIdAsNonRetryableFailure() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		MockSendApiClient client = new MockSendApiClient(
			restClientBuilder.baseUrl("http://localhost:8081").build()
		);
		Notification notification = createSmsNotification();

		server.expect(once(), requestTo("http://localhost:8081/mock/send"))
			.andRespond(withSuccess("""
				{
				  "result": "SUCCESS",
				  "requestId": "   ",
				  "channelType": "SMS",
				  "processedAt": "2026-07-22T00:00:00Z",
				  "latencyMs": 120
				}
				""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.send(notification, NotificationChannel.SMS))
			.isInstanceOfSatisfying(ExternalNotificationSendException.class, exception -> {
				assertThat(exception.isRetryable()).isFalse();
				assertThat(exception.getErrorCode()).isEqualTo("MOCK_REQUEST_ID_MISMATCH");
			});

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
