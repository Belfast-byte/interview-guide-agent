package interview.guide.infrastructure.codeanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.BusinessException;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnchor;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisProperties;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class S3ZipCodeAnchorCatalogTest {

  private static final String OWNED_KEY = "code-analysis/tenant-a/session-1/repo.zip";

  @Test
  @DisplayName("仓库快照只接受真实存在的文件和行号")
  void shouldValidateExactFileAndLineInSnapshot() throws Exception {
    FileStorageService storageService = mock(FileStorageService.class);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      zip.putNextEntry(new ZipEntry("src/OrderService.java"));
      zip.write("line one\nline two\n".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    when(storageService.downloadFile(OWNED_KEY)).thenReturn(bytes.toByteArray());
    S3ZipCodeAnchorCatalog catalog = new S3ZipCodeAnchorCatalog(
        storageService,
        new CodeAnalysisProperties()
    );
    CodeAnchor existing = new CodeAnchor("src/OrderService.java", 2);
    CodeAnchor missingLine = new CodeAnchor("src/OrderService.java", 3);
    CodeAnchor missingFile = new CodeAnchor("src/Missing.java", 1);

    assertThat(catalog.findMissing(
        "tenant-a",
        "session-1",
        OWNED_KEY,
        Set.of(existing, missingLine, missingFile)
    )).containsExactlyInAnyOrder(missingLine, missingFile);
  }

  @Test
  @DisplayName("越界 key(伪造他人租户前缀)在下载前按跨租户 404 拒绝")
  void shouldRejectAnotherTenantPrefixBeforeDownload() {
    FileStorageService storageService = mock(FileStorageService.class);
    S3ZipCodeAnchorCatalog catalog = new S3ZipCodeAnchorCatalog(
        storageService,
        new CodeAnalysisProperties()
    );
    CodeAnchor anchor = new CodeAnchor("src/OrderService.java", 2);

    assertThatThrownBy(() -> catalog.findMissing(
        "tenant-a",
        "session-1",
        "code-analysis/tenant-b/session-1/repo.zip",
        Set.of(anchor)
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
    S3ZipCodeAnchorCatalog catalog = new S3ZipCodeAnchorCatalog(
        storageService,
        new CodeAnalysisProperties()
    );
    CodeAnchor anchor = new CodeAnchor("src/OrderService.java", 2);

    assertThatThrownBy(() -> catalog.findMissing(
        "tenant-a",
        "session-1",
        "resumes/2026/01/01/abc.pdf",
        Set.of(anchor)
    ))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("code", 404)
        .hasMessage("代码仓库快照不存在");

    verifyNoInteractions(storageService);
  }
}
