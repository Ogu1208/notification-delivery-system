package ogu1208.notificationdeliverysystem.adapter.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import ogu1208.notificationdeliverysystem.application.notification.required.ExternalNotificationSendException;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;

@Component
public class MockSendApiClient {

	private static final Logger log = LoggerFactory.getLogger(MockSendApiClient.class);

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	@Autowired
	public MockSendApiClient(
		RestClient.Builder restClientBuilder,
		ObjectMapper objectMapper,
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
		this.objectMapper = objectMapper;
	}

	MockSendApiClient(RestClient restClient) {
		this(restClient, new ObjectMapper());
	}

	MockSendApiClient(RestClient restClient, ObjectMapper objectMapper) {
		this.restClient = restClient;
		this.objectMapper = objectMapper;
	}

	public void send(Notification notification, NotificationChannel channel) {
		long startedAt = System.nanoTime();
		try {
			MockSendResponse body = restClient.post()
				.uri("/mock/send")
				.contentType(MediaType.APPLICATION_JSON)
				.body(MockSendRequest.from(notification, channel))
				.exchange((request, response) -> {
					if (response.getStatusCode().isError()) {
						throw mapErrorResponse(response, startedAt);
					}
					MockSendResponse parsed = response.bodyTo(MockSendResponse.class);
					if (parsed == null) {
						throw ExternalNotificationSendException.nonRetryable(
							"Mock Send API 성공 응답 본문이 비어 있습니다",
							"MOCK_EMPTY_SUCCESS_BODY",
							response.getStatusCode().value(),
							elapsedTimeMs(startedAt),
							null
						);
					}
					return parsed;
				});
			log.info(
				"Mock Send API 발송 성공. notificationId={}, requestId={}, channel={}, httpStatus={}, elapsedTimeMs={}, externalLatencyMs={}",
				notification.getId(),
				notification.getRequestId().value(),
				channel,
				HttpStatus.OK.value(),
				elapsedTimeMs(startedAt),
				body == null ? null : body.latencyMs()
			);
		} catch (ExternalNotificationSendException e) {
			throw e;
		} catch (ResourceAccessException e) {
			throw ExternalNotificationSendException.retryable(
				"Mock Send API timeout 또는 네트워크 오류",
				"MOCK_SEND_TIMEOUT",
				null,
				elapsedTimeMs(startedAt),
				e
			);
		} catch (RestClientException e) {
			throw ExternalNotificationSendException.nonRetryable(
				"Mock Send API 응답 처리 실패",
				"MOCK_RESPONSE_PARSE_FAILED",
				null,
				elapsedTimeMs(startedAt),
				e
			);
		}
	}

	private ExternalNotificationSendException mapErrorResponse(ClientHttpResponse response, long startedAt) throws IOException {
		int statusCode = response.getStatusCode().value();
		MockSendErrorResponse errorResponse = readErrorResponse(response, statusCode, startedAt);
		boolean retryable = statusCode == 429 || response.getStatusCode().is5xxServerError();
		long elapsedTimeMs = elapsedTimeMs(startedAt);

		if (retryable) {
			return ExternalNotificationSendException.retryable(
				errorResponse.message(),
				errorResponse.code(),
				statusCode,
				elapsedTimeMs,
				null
			);
		}

		return ExternalNotificationSendException.nonRetryable(
			errorResponse.message(),
			errorResponse.code(),
			statusCode,
			elapsedTimeMs,
			null
		);
	}

	private MockSendErrorResponse readErrorResponse(ClientHttpResponse response, int statusCode, long startedAt) {
		try {
			byte[] rawBytes = response.getBody().readAllBytes();
			if (rawBytes.length == 0) {
				return new MockSendErrorResponse(
					"MOCK_HTTP_" + statusCode,
					"Mock Send API 에러 응답 본문이 비어 있습니다"
				);
			}

			String raw = new String(rawBytes, StandardCharsets.UTF_8);
			return objectMapper.readValue(raw, MockSendErrorResponse.class);
		} catch (Exception e) {
			throw ExternalNotificationSendException.nonRetryable(
				"Mock Send API 에러 응답 파싱 실패",
				"MOCK_ERROR_RESPONSE_PARSE_FAILED",
				statusCode,
				elapsedTimeMs(startedAt),
				e
			);
		}
	}

	private long elapsedTimeMs(long startedAt) {
		return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
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

	private record MockSendResponse(
		String result,
		String requestId,
		NotificationChannel channelType,
		String processedAt,
		Long latencyMs
	) {
	}

	private record MockSendErrorResponse(
		String code,
		String message
	) {
	}
}
