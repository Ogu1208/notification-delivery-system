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
			1234L,
			LocalDateTime.of(2026, 7, 25, 10, 0),
			LocalDateTime.of(2026, 8, 1, 10, 0)
		);

		String encoded = cursor.encode();
		NotificationSearchCursor decoded = NotificationSearchCursor.decode(encoded);

		assertThat(decoded).isEqualTo(cursor);
	}

	@Test
	@DisplayName("커서를 왕복시키면 첫 페이지에서 확정한 조회 구간이 보존된다")
	void encodeDecodePreservesSearchWindow() {
		LocalDateTime from = LocalDateTime.of(2026, 7, 25, 10, 0, 0, 123_456_789);
		LocalDateTime to = LocalDateTime.of(2026, 8, 1, 10, 0, 0, 123_456_789);
		NotificationSearchCursor cursor = new NotificationSearchCursor(
			LocalDateTime.of(2026, 8, 1, 12, 34, 56),
			1234L,
			from,
			to
		);

		NotificationSearchCursor decoded = NotificationSearchCursor.decode(cursor.encode());

		assertThat(decoded.from()).isEqualTo(from);
		assertThat(decoded.to()).isEqualTo(to);
	}

	@Test
	@DisplayName("조회 구간이 없는 커서는 디코딩에서 예외를 던진다")
	void decodeRejectsCursorWithoutSearchWindow() {
		// {"createdAt":"2026-08-01T12:34:56","id":1234} 를 Base64로 인코딩한 값 (구간 필드 추가 이전 형식)
		String legacyCursor = "eyJjcmVhdGVkQXQiOiIyMDI2LTA4LTAxVDEyOjM0OjU2IiwiaWQiOjEyMzR9";

		assertThatThrownBy(() -> NotificationSearchCursor.decode(legacyCursor))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("cursor 형식이 올바르지 않습니다");
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
		assertThatThrownBy(() -> new NotificationSearchCursor(
			null,
			1L,
			LocalDateTime.of(2026, 7, 25, 10, 0),
			LocalDateTime.of(2026, 8, 1, 10, 0)
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("cursor createdAt은 필수입니다");
	}

	@Test
	@DisplayName("id가 null이면 생성자에서 예외를 던진다")
	void rejectsNullId() {
		assertThatThrownBy(() -> new NotificationSearchCursor(
			LocalDateTime.now(),
			null,
			LocalDateTime.of(2026, 7, 25, 10, 0),
			LocalDateTime.of(2026, 8, 1, 10, 0)
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("cursor id는 필수입니다");
	}

	@Test
	@DisplayName("조회 구간 from이 null이면 생성자에서 예외를 던진다")
	void rejectsNullFrom() {
		assertThatThrownBy(() -> new NotificationSearchCursor(
			LocalDateTime.of(2026, 8, 1, 12, 34, 56),
			1L,
			null,
			LocalDateTime.of(2026, 8, 1, 10, 0)
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("cursor 조회 구간 from, to는 필수입니다");
	}

	@Test
	@DisplayName("조회 구간 to가 null이면 생성자에서 예외를 던진다")
	void rejectsNullTo() {
		assertThatThrownBy(() -> new NotificationSearchCursor(
			LocalDateTime.of(2026, 8, 1, 12, 34, 56),
			1L,
			LocalDateTime.of(2026, 7, 25, 10, 0),
			null
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("cursor 조회 구간 from, to는 필수입니다");
	}
}
