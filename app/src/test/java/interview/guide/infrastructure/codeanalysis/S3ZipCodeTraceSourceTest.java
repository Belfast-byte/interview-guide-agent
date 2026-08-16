package interview.guide.infrastructure.codeanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.BusinessException;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisProperties;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class S3ZipCodeTraceSourceTest {

  private static final String OWNED_KEY = "code-analysis/tenant-a/session-1/repo.zip";

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
    when(storageService.downloadFile(OWNED_KEY)).thenReturn(bytes.toByteArray());
    S3ZipCodeTraceSource source = new S3ZipCodeTraceSource(
        storageService,
        new CodeAnalysisProperties()
    );

    assertThat(source.trace("tenant-a", "session-1", OWNED_KEY, "OrderService", 10))
        .singleElement()
        .satisfies(match -> {
          assertThat(match.anchor().display()).isEqualTo("src/OrderService.java:1");
          assertThat(match.sourceLine()).isEqualTo("class OrderService {");
        });
  }

  @Test
  @DisplayName("越界 key(他人租户前缀)在下载前按跨租户 404 拒绝")
  void shouldRejectAnotherTenantPrefixBeforeDownload() {
    FileStorageService storageService = mock(FileStorageService.class);
    S3ZipCodeTraceSource source = new S3ZipCodeTraceSource(
        storageService,
        new CodeAnalysisProperties()
    );

    assertThatThrownBy(() -> source.trace(
        "tenant-a",
        "session-1",
        "code-analysis/tenant-b/session-1/repo.zip",
        "OrderService",
        10
    ))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("code", 404)
        .hasMessage("代码仓库快照不存在");

    verifyNoInteractions(storageService);
  }

  @Test
  @DisplayName("越界 key(平台其他命名空间)在下载前按跨租户 404 拒绝")
  void shouldRejectOutsideNamespaceBeforeDownload() {
    FileStorageService storageService = mock(FileStorageService.class);
    S3ZipCodeTraceSource source = new S3ZipCodeTraceSource(
        storageService,
        new CodeAnalysisProperties()
    );

    assertThatThrownBy(() -> source.trace(
        "tenant-a",
        "session-1",
        "sandbox/sources/session-1/00000000-0000-0000-0000-000000000001.java",
        "OrderService",
        10
    ))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("code", 404)
        .hasMessage("代码仓库快照不存在");

    verifyNoInteractions(storageService);
  }
}
