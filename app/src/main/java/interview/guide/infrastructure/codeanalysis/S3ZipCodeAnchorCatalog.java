package interview.guide.infrastructure.codeanalysis;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnchor;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnchorCatalog;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class S3ZipCodeAnchorCatalog implements CodeAnchorCatalog {

  private final FileStorageService fileStorageService;

  @Override
  public Set<CodeAnchor> findMissing(String repositoryRef, Set<CodeAnchor> anchors) {
    Set<CodeAnchor> missing = new LinkedHashSet<>(anchors);
    byte[] snapshot = fileStorageService.downloadFile(repositoryRef);
    try (ZipInputStream zip = new ZipInputStream(
        new ByteArrayInputStream(snapshot),
        StandardCharsets.UTF_8
    )) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null && !missing.isEmpty()) {
        if (entry.isDirectory()) {
          continue;
        }
        String file = entry.getName().replace('\\', '/');
        Set<CodeAnchor> fileAnchors = missing.stream()
            .filter(anchor -> anchor.file().equals(file))
            .collect(Collectors.toSet());
        if (fileAnchors.isEmpty()) {
          continue;
        }
        long lineCount = new String(zip.readAllBytes(), StandardCharsets.UTF_8)
            .lines()
            .count();
        missing.removeIf(anchor -> anchor.file().equals(file) && anchor.line() <= lineCount);
      }
      return Set.copyOf(missing);
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED, "仓库快照读取失败", e);
    }
  }
}
