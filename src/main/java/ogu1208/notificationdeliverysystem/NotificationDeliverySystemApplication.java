package ogu1208.notificationdeliverysystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NotificationDeliverySystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationDeliverySystemApplication.class, args);
	}

}
