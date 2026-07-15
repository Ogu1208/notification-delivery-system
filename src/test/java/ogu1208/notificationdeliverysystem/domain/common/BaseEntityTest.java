package ogu1208.notificationdeliverysystem.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BaseEntityTest {

	@Test
	void baseEntityDoesNotExposeDeletedAt() {
		assertThat(BaseEntity.class.getDeclaredFields())
			.extracting("name")
			.contains("createdAt", "updatedAt")
			.doesNotContain("deletedAt");
	}
}
