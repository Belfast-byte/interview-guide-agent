package interview.guide.modules.interview.agent.adaptive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptivePackageIsolationTest {

  @Test
  @DisplayName("新 Agent 实现不能依赖旧 MVP 包")
  void shouldNotDependOnLegacyAgentPackages() throws IOException {
    Path sourceRoot = Path.of(
        "src/main/java/interview/guide/modules/interview/agent/adaptive"
    );
    Pattern legacyPackage = Pattern.compile(
        "interview\\.guide\\.modules\\.interview\\.agent\\.(?!adaptive\\.)"
    );

    try (var files = Files.walk(sourceRoot)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        assertThat(Files.readString(file))
            .as("旧 MVP 依赖出现在 %s", file)
            .doesNotContainPattern(legacyPackage);
      }
    }
  }
}
