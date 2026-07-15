package ogu1208.notificationdeliverysystem.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

class BaseEntityTest {

	@Test
	@DisplayName("공통 엔티티는 생성일과 수정일만 제공하고 삭제일은 제공하지 않는다")
	void baseEntityDoesNotExposeDeletedAt() {
		assertThat(BaseEntity.class.getDeclaredFields())
			.extracting("name")
			.contains("createdAt", "updatedAt")
			.doesNotContain("deletedAt");
	}

	@Test
	@DisplayName("공통 엔티티의 생성일과 수정일은 Spring Data JPA 감사 어노테이션을 사용한다")
	void baseEntityUsesSpringDataJpaAuditingAnnotations() throws NoSuchFieldException {
		assertThat(BaseEntity.class.getDeclaredField("createdAt").getType()).isEqualTo(LocalDateTime.class);
		assertThat(BaseEntity.class.getDeclaredField("createdAt").isAnnotationPresent(CreatedDate.class)).isTrue();
		assertThat(BaseEntity.class.getDeclaredField("updatedAt").getType()).isEqualTo(LocalDateTime.class);
		assertThat(BaseEntity.class.getDeclaredField("updatedAt").isAnnotationPresent(LastModifiedDate.class)).isTrue();
	}
}
