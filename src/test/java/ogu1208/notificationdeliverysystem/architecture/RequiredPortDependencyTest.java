package ogu1208.notificationdeliverysystem.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequiredPortDependencyTest {

	private static final Path REQUIRED_PORT_DIR = Path.of(
		"src/main/java/ogu1208/notificationdeliverysystem/application/notification/required"
	);

	@Test
	@DisplayName("required port는 Spring Data 타입에 의존하지 않는다")
	void requiredPortsDoNotDependOnSpringData() throws IOException {
		List<Path> javaFiles = Files.walk(REQUIRED_PORT_DIR)
			.filter(path -> path.toString().endsWith(".java"))
			.toList();

		List<String> springDataImports = javaFiles.stream()
			.flatMap(path -> readLines(path).stream())
			.filter(line -> line.contains("org.springframework.data"))
			.toList();

		assertThat(springDataImports).isEmpty();
	}

	private List<String> readLines(Path path) {
		try {
			return Files.readAllLines(path);
		} catch (IOException e) {
			throw new IllegalStateException("파일을 읽을 수 없습니다: " + path, e);
		}
	}
}
