package ogu1208.notificationdeliverysystem.support.fixture;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationRegisterRequest;
import org.springframework.test.util.ReflectionTestUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationFixture {

	private String requestId = "req-001";
	private String receiver = "user@example.com";
	private NotificationChannel channel = NotificationChannel.EMAIL;
	private String title = "welcome";
	private String content = "hello";

	public static NotificationRegisterRequest createRequest() {
		return builder().build();
	}

	public static Notification createNotification() {
		return Notification.register(createRequest());
	}

	public static Notification createNotification(Long id) {
		Notification notification = createNotification();
		ReflectionTestUtils.setField(notification, "id", id);
		return notification;
	}

	public static NotificationFixture builder() {
		return new NotificationFixture();
	}

	public NotificationRegisterRequest build() {
		return new NotificationRegisterRequest(requestId, receiver, channel, title, content);
	}

	public NotificationFixture withRequestId(String requestId) {
		this.requestId = requestId;
		return this;
	}

	public NotificationFixture withReceiver(String receiver) {
		this.receiver = receiver;
		return this;
	}

	public NotificationFixture withChannel(NotificationChannel channel) {
		this.channel = channel;
		return this;
	}

	public NotificationFixture withTitle(String title) {
		this.title = title;
		return this;
	}

	public NotificationFixture withContent(String content) {
		this.content = content;
		return this;
	}
}
