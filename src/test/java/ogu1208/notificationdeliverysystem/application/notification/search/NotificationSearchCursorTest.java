package ogu1208.notificationdeliverysystem.application.notification.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationSearchCursorTest {

	@Test
	@DisplayName("커서를 인코딩한 뒤 디코딩하면 원래 값이 복원된다")
	void encodeDecodeRoundTrip() {
		NotificationSearchCursor cursor = new NotificationSearchCursor(
			LocalDateTime.of(2026, 8, 1, 12, 34, 56),
			1234L
		);

		String encoded = cursor.encode();
		NotificationSearchCursor decoded = NotificationSearchCursor.decode(encoded);

		assertThat(decoded).isEqualTo(cursor);
	}

	@Test
	@DisplayName("커서 값이 비어 있으면 예외를 던진다")
	void decodeRejectsBlank() {
		assertThatThrownBy(() -> NotificationSearchCursor.decode(""))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("cursor 값이 비어 있습니다");
	}

	@Test
	@DisplayName("커서 값이 null이면 예외를 던진다")
	void decodeRejectsNull() {
		assertThatThrownBy(() -> NotificationSearchCursor.decode(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("cursor 값이 비어 있습니다");
	}

	@Test
	@DisplayName("Base64로 디코딩되지 않는 커서는 예외를 던진다")
	void decodeRejectsInvalidBase64() {
		assertThatThrownBy(() -> NotificationSearchCursor.decode("not-a-valid-base64!"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("cursor 형식이 올바르지 않습니다");
	}

	@Test
	@DisplayName("Base64는 유효하지만 JSON이 아닌 커서는 예외를 던진다")
	void decodeRejectsNonJsonPayload() {
		// "foobar"를 Base64로 인코딩한 값 → 디코딩되지만 JSON 파싱 실패
		assertThatThrownBy(() -> NotificationSearchCursor.decode("Zm9vYmFy"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("cursor 형식이 올바르지 않습니다");
	}

	@Test
	@DisplayName("createdAt이 null이면 생성자에서 예외를 던진다")
	void rejectsNullCreatedAt() {
		assertThatThrownBy(() -> new NotificationSearchCursor(null, 1L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("cursor createdAt은 필수입니다");
	}

	@Test
	@DisplayName("id가 null이면 생성자에서 예외를 던진다")
	void rejectsNullId() {
		assertThatThrownBy(() -> new NotificationSearchCursor(LocalDateTime.now(), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("cursor id는 필수입니다");
	}
}
