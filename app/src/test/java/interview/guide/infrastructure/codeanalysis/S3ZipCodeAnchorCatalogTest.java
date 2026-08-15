package interview.guide.infrastructure.codeanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnchor;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

class S3ZipCodeAnchorCatalogTest {

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
    when(storageService.downloadFile("repos/one.zip")).thenReturn(bytes.toByteArray());
    S3ZipCodeAnchorCatalog catalog = new S3ZipCodeAnchorCatalog(storageService);
    CodeAnchor existing = new CodeAnchor("src/OrderService.java", 2);
    CodeAnchor missingLine = new CodeAnchor("src/OrderService.java", 3);
    CodeAnchor missingFile = new CodeAnchor("src/Missing.java", 1);

    assertThat(catalog.findMissing(
        "repos/one.zip",
        Set.of(existing, missingLine, missingFile)
    )).containsExactlyInAnyOrder(missingLine, missingFile);
  }
}
