package ogu1208.notificationdeliverysystem.support.fixture;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationRegisterRequestFixture {

	private String requestId = "req-001";
	private String receiver = "user@example.com";
	private NotificationChannel channel = NotificationChannel.EMAIL;
	private String title = "welcome";
	private String content = "hello";

	public static NotificationRegisterRequest create() {
		return builder().build();
	}

	public static NotificationRegisterRequestFixture builder() {
		return new NotificationRegisterRequestFixture();
	}

	public NotificationRegisterRequest build() {
		return new NotificationRegisterRequest(requestId, receiver, channel, title, content);
	}

	public NotificationRegisterRequestFixture withRequestId(String requestId) {
		this.requestId = requestId;
		return this;
	}

	public NotificationRegisterRequestFixture withReceiver(String receiver) {
		this.receiver = receiver;
		return this;
	}

	public NotificationRegisterRequestFixture withChannel(NotificationChannel channel) {
		this.channel = channel;
		return this;
	}

	public NotificationRegisterRequestFixture withTitle(String title) {
		this.title = title;
		return this;
	}

	public NotificationRegisterRequestFixture withContent(String content) {
		this.content = content;
		return this;
	}
}
