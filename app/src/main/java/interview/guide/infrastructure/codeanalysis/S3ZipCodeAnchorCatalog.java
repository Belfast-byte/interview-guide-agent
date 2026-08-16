package interview.guide.infrastructure.codeanalysis;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnchor;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnchorCatalog;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisProperties;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.CodeAnalysisRepositoryKeyPolicy;
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
  private final CodeAnalysisProperties properties;

  @Override
  public Set<CodeAnchor> findMissing(
      String tenantId,
      String sessionId,
      String repositoryRef,
      Set<CodeAnchor> anchors
  ) {
    CodeAnalysisRepositoryKeyPolicy.requireOwned(repositoryRef, tenantId, sessionId);
    Set<CodeAnchor> missing = new LinkedHashSet<>(anchors);
    byte[] snapshot = fileStorageService.downloadFile(repositoryRef);
    if (snapshot.length > properties.getMaxSnapshotBytes()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库快照超过大小上限");
    }
    try (ZipInputStream zip = new ZipInputStream(
        new ByteArrayInputStream(snapshot),
        StandardCharsets.UTF_8
    )) {
      ZipEntry entry;
      int fileCount = 0;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        fileCount++;
        if (fileCount > properties.getMaxSnapshotFiles()) {
          throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库快照超过文件数上限");
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
