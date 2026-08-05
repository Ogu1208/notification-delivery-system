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
	@DisplayName("잘못된 형식의 커서는 예외를 던진다")
	void decodeRejectsMalformed() {
		assertThatThrownBy(() -> NotificationSearchCursor.decode("not-a-valid-base64!"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("cursor 형식이 올바르지 않습니다");
	}
}
