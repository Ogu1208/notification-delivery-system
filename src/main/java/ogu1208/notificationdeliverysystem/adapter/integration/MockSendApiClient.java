package ogu1208.notificationdeliverysystem.adapter.integration;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;

@Component
public class MockSendApiClient {

	private final RestClient restClient;

	@Autowired
	public MockSendApiClient(
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

	MockSendApiClient(RestClient restClient) {
		this.restClient = restClient;
	}

	public void send(Notification notification, NotificationChannel channel) {
		restClient.post()
			.uri("/mock/send")
			.contentType(MediaType.APPLICATION_JSON)
			.body(MockSendRequest.from(notification, channel))
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

		private static MockSendRequest from(Notification notification, NotificationChannel channel) {
			return new MockSendRequest(
				notification.getRequestId().value(),
				channel,
				notification.getReceiver(),
				notification.getContent(),
				Map.of("notificationId", notification.getId())
			);
		}
	}
}
