package ogu1208.notificationdeliverysystem.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

class BaseEntityTest {

	@Test
	void baseEntityDoesNotExposeDeletedAt() {
		assertThat(BaseEntity.class.getDeclaredFields())
			.extracting("name")
			.contains("createdAt", "updatedAt")
			.doesNotContain("deletedAt");
	}

	@Test
	void baseEntityUsesSpringDataJpaAuditingAnnotations() throws NoSuchFieldException {
		assertThat(BaseEntity.class.getDeclaredField("createdAt").getType()).isEqualTo(LocalDateTime.class);
		assertThat(BaseEntity.class.getDeclaredField("createdAt").isAnnotationPresent(CreatedDate.class)).isTrue();
		assertThat(BaseEntity.class.getDeclaredField("updatedAt").getType()).isEqualTo(LocalDateTime.class);
		assertThat(BaseEntity.class.getDeclaredField("updatedAt").isAnnotationPresent(LastModifiedDate.class)).isTrue();
	}
}
