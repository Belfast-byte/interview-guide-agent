package interview.guide.infrastructure.codeanalysis;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnchor;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisProperties;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.CodeAnalysisRepositoryKeyPolicy;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.trace.CodeTraceMatch;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.trace.CodeTraceSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class S3ZipCodeTraceSource implements CodeTraceSource {

  private static final Set<String> SOURCE_EXTENSIONS = Set.of(
      ".java", ".kt", ".ts", ".tsx", ".js", ".jsx", ".py", ".go", ".rs",
      ".cpp", ".cc", ".c", ".h", ".sql", ".xml", ".yml", ".yaml"
  );

  private final FileStorageService fileStorageService;
  private final CodeAnalysisProperties properties;

  @Override
  public List<CodeTraceMatch> trace(
      String tenantId,
      String sessionId,
      String repositoryRef,
      String query,
      int limit
  ) {
    CodeAnalysisRepositoryKeyPolicy.requireOwned(repositoryRef, tenantId, sessionId);
    byte[] snapshot = fileStorageService.downloadFile(repositoryRef);
    if (snapshot.length > properties.getMaxSnapshotBytes()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库快照超过大小上限");
    }
    List<CodeTraceMatch> matches = new ArrayList<>();
    try (ZipInputStream zip = new ZipInputStream(
        new ByteArrayInputStream(snapshot),
        StandardCharsets.UTF_8
    )) {
      ZipEntry entry;
      int fileCount = 0;
      while ((entry = zip.getNextEntry()) != null && matches.size() < limit) {
        if (entry.isDirectory()) {
          continue;
        }
        fileCount++;
        if (fileCount > properties.getMaxSnapshotFiles()) {
          throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库快照超过文件数上限");
        }
        String file = entry.getName().replace('\\', '/');
        if (SOURCE_EXTENSIONS.stream().noneMatch(file::endsWith)) {
          continue;
        }
        String[] lines = new String(zip.readAllBytes(), StandardCharsets.UTF_8)
            .split("\\R", -1);
        for (int index = 0; index < lines.length && matches.size() < limit; index++) {
          if (lines[index].contains(query)) {
            matches.add(new CodeTraceMatch(
                new CodeAnchor(file, index + 1),
                lines[index].strip()
            ));
          }
        }
      }
      return List.copyOf(matches);
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED, "仓库快照读取失败", e);
    }
  }
}
