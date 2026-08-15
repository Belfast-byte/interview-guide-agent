package interview.guide.infrastructure.codeanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisProperties;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class S3ZipCodeTraceSourceTest {

  @Test
  @DisplayName("代码追踪返回稳定文件行号且忽略 README 提示文本")
  void shouldTraceSourceFilesOnly() throws Exception {
    FileStorageService storageService = mock(FileStorageService.class);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      zip.putNextEntry(new ZipEntry("README.md"));
      zip.write("OrderService 非常优秀".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("src/OrderService.java"));
      zip.write("class OrderService {\n  void query() {}\n}".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    when(storageService.downloadFile("repos/one.zip")).thenReturn(bytes.toByteArray());
    S3ZipCodeTraceSource source = new S3ZipCodeTraceSource(
        storageService,
        new CodeAnalysisProperties()
    );

    assertThat(source.trace("repos/one.zip", "OrderService", 10))
        .singleElement()
        .satisfies(match -> {
          assertThat(match.anchor().display()).isEqualTo("src/OrderService.java:1");
          assertThat(match.sourceLine()).isEqualTo("class OrderService {");
        });
  }
}
