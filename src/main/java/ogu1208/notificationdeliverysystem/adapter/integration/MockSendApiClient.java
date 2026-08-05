package ogu1208.notificationdeliverysystem.adapter.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import ogu1208.notificationdeliverysystem.application.notification.required.ExternalNotificationSendException;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;

// Mock 발송 관련 HTTP 클라이언트는 실제로 mock을 호출하는 worker 컨테이너에만 필요.
// app-api에는 bean 자체 생성 안 하도록 조건부 등록.
// HC5의 백그라운드 활동(evictor 스레드 등)이 app-api의 요청 처리에 영향을 주는 가설 검증.
@Component
@ConditionalOnProperty(name = "notification.role.worker-enabled", havingValue = "true")
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
		@Value("${notification.mock-send.read-timeout-ms:3000}") long readTimeoutMs,
		@Value("${notification.mock-send.pool.max-total:200}") int poolMaxTotal,
		@Value("${notification.mock-send.pool.max-per-route:100}") int poolMaxPerRoute,
		@Value("${notification.mock-send.pool.validate-after-inactivity-sec:10}") int validateAfterInactivitySec,
		@Value("${notification.mock-send.pool.time-to-live-min:10}") int timeToLiveMin,
		@Value("${notification.mock-send.pool.evict-idle-sec:30}") int evictIdleSec
	) {
		// Socket 레벨 설정. SO_KEEPALIVE로 TCP half-open 감지.
		// SO_TIMEOUT은 소켓 레벨 read timeout (RequestConfig.responseTimeout과 중복 피하려 여기 한 곳에만 둠).
		SocketConfig socketConfig = SocketConfig.custom()
			.setSoKeepAlive(true)
			.setSoTimeout(Timeout.of(readTimeoutMs, TimeUnit.MILLISECONDS))
			.build();

		// Connection 레벨 설정. validateAfterInactivity로 stale connection 재사용 전 검증.
		// timeToLive로 커넥션 최대 재사용 시간 상한 (오래된 것 강제 폐기).
		ConnectionConfig connectionConfig = ConnectionConfig.custom()
			.setConnectTimeout(Timeout.of(connectTimeoutMs, TimeUnit.MILLISECONDS))
			.setValidateAfterInactivity(TimeValue.ofSeconds(validateAfterInactivitySec))
			.setTimeToLive(TimeValue.ofMinutes(timeToLiveMin))
			.build();

		PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
			.setMaxConnTotal(poolMaxTotal)
			.setMaxConnPerRoute(poolMaxPerRoute)
			.setDefaultSocketConfig(socketConfig)
			.setDefaultConnectionConfig(connectionConfig)
			.build();

		// Request 레벨 설정.
		// connectionRequestTimeout: pool에서 커넥션 대여 대기 시간 (pool 소진 시).
		// responseTimeout: 응답 대기 시간 (HC5의 request-level read timeout).
		RequestConfig requestConfig = RequestConfig.custom()
			.setConnectionRequestTimeout(Timeout.of(connectTimeoutMs, TimeUnit.MILLISECONDS))
			.setResponseTimeout(Timeout.of(readTimeoutMs, TimeUnit.MILLISECONDS))
			.build();

		// evictIdleConnections / evictExpiredConnections로 백그라운드 정리 스레드 활성화.
		// idle timeout 넘거나 만료된 커넥션을 자동 폐기해 pool에 stale 커넥션이 쌓이지 않게 한다.
		CloseableHttpClient httpClient = HttpClients.custom()
			.setConnectionManager(connectionManager)
			.setDefaultRequestConfig(requestConfig)
			.evictIdleConnections(TimeValue.ofSeconds(evictIdleSec))
			.evictExpiredConnections()
			.build();

		HttpComponentsClientHttpRequestFactory requestFactory =
			new HttpComponentsClientHttpRequestFactory(httpClient);

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
