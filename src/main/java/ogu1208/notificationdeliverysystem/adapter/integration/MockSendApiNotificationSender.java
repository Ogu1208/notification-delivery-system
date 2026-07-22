package ogu1208.notificationdeliverysystem.adapter.integration;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ogu1208.notificationdeliverysystem.application.notification.required.ExternalNotificationSender;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;

@Component
@ConditionalOnProperty(name = "notification.mock-send.type", havingValue = "http")
public class MockSendApiNotificationSender implements ExternalNotificationSender {

	private final RestClient restClient;

	public MockSendApiNotificationSender(
		RestClient.Builder restClientBuilder,
		@Value("${notification.mock-send.base-url:http://localhost:8081}") String baseUrl,
		@Value("${notification.mock-send.connect-timeout-ms:1000}") long connectTimeoutMs,
		@Value("${notification.mock-send.read-timeout-ms:3000}") long readTimeoutMs
	) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
		requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
		this.restClient = restClientBuilder
			.baseUrl(baseUrl)
			.requestFactory(requestFactory)
			.build();
	}

	MockSendApiNotificationSender(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public void send(Notification notification) {
		restClient.post()
			.uri("/mock/send")
			.contentType(MediaType.APPLICATION_JSON)
			.body(MockSendRequest.from(notification))
			.retrieve()
			.toBodilessEntity();
	}

	private record MockSendRequest(
		String requestId,
		NotificationChannel channelType,
		String receiver,
		String message,
		Map<String, Object> metadata
	) {

		private static MockSendRequest from(Notification notification) {
			return new MockSendRequest(
				notification.getRequestId().value(),
				notification.getChannel(),
				notification.getReceiver(),
				notification.getContent(),
				Map.of("notificationId", notification.getId())
			);
		}
	}
}
