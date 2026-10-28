package ogu1208.notificationdeliverysystem.application.notification.search;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 커서 기반 페이지네이션의 커서 값.
 *
 * <p>정렬 순서 {@code (createdAt DESC, id DESC)}의 마지막 위치를 표현한다.
 * {@code createdAt}만으로는 같은 시각에 생성된 알림 사이에서 순서가 유일하지 않아
 * 페이지 경계에서 중복 / 누락이 발생할 수 있으므로 {@code id}를 tie-break 키로 함께 담는다.
 *
 * <p>첫 페이지에서 확정한 조회 구간 {@code (from, to)}도 함께 담는다. 구간을 담지 않으면
 * {@code from} / {@code to}를 생략한 후속 페이지 요청이 매번 {@code now()} 기준으로 구간을 다시
 * 계산해 하한선이 앞으로 밀리고, 커서보다 과거이면서 새 하한선보다 오래된 알림이 어느 페이지에도
 * 나오지 않는다.
 *
 * <p>클라이언트에게는 Base64 URL-safe로 인코딩한 opaque 문자열로 제공한다.
 * 클라이언트는 값을 해석하지 않고 서버가 응답에 넣어 준 값을 다음 요청에 그대로 다시 보낸다.
 */
public record NotificationSearchCursor(LocalDateTime createdAt, Long id, LocalDateTime from, LocalDateTime to) {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
		.registerModule(new JavaTimeModule())
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
		.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	public NotificationSearchCursor {
		if (createdAt == null) {
			throw new IllegalArgumentException("cursor createdAt은 필수입니다");
		}
		if (id == null) {
			throw new IllegalArgumentException("cursor id는 필수입니다");
		}
		if (from == null || to == null) {
			throw new IllegalArgumentException("cursor 조회 구간 from, to는 필수입니다");
		}
	}

	public String encode() {
		try {
			byte[] json = OBJECT_MAPPER.writeValueAsBytes(this);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("cursor 직렬화에 실패했습니다", e);
		}
	}

	public static NotificationSearchCursor decode(String encoded) {
		if (encoded == null || encoded.isBlank()) {
			throw new IllegalArgumentException("cursor 값이 비어 있습니다");
		}
		byte[] json;
		try {
			json = Base64.getUrlDecoder().decode(encoded);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다", e);
		}
		try {
			return OBJECT_MAPPER.readValue(json, NotificationSearchCursor.class);
		} catch (IOException e) {
			throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다", e);
		}
	}
}
