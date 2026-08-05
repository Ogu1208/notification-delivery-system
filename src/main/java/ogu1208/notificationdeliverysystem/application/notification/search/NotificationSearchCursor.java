package ogu1208.notificationdeliverysystem.application.notification.search;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

public record NotificationSearchCursor(LocalDateTime createdAt, Long id) {

	public NotificationSearchCursor {
		if (createdAt == null) {
			throw new IllegalArgumentException("cursor createdAt은 필수입니다");
		}
		if (id == null) {
			throw new IllegalArgumentException("cursor id는 필수입니다");
		}
	}

	public String encode() {
		String json = "{\"c\":\"" + createdAt + "\",\"i\":" + id + "}";
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

	public static NotificationSearchCursor decode(String encoded) {
		if (encoded == null || encoded.isBlank()) {
			throw new IllegalArgumentException("cursor 값이 비어 있습니다");
		}
		String json;
		try {
			json = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다", e);
		}
		return parse(json);
	}

	private static NotificationSearchCursor parse(String json) {
		try {
			int cStart = json.indexOf("\"c\":\"") + 5;
			int cEnd = json.indexOf('"', cStart);
			int iStart = json.indexOf("\"i\":", cEnd) + 4;
			int iEnd = json.indexOf('}', iStart);
			LocalDateTime createdAt = LocalDateTime.parse(json.substring(cStart, cEnd));
			Long id = Long.parseLong(json.substring(iStart, iEnd).trim());
			return new NotificationSearchCursor(createdAt, id);
		} catch (DateTimeParseException | NumberFormatException | IndexOutOfBoundsException e) {
			throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다", e);
		}
	}
}
