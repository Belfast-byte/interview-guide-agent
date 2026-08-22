package interview.guide.modules.interview.agent.adaptive.codeanalysis.repo;

import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisPersistenceService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 代码分析保留策略定时任务。
 */
@Component
@RequiredArgsConstructor
public class CodeAnalysisRetentionScheduler {

  private final CodeAnalysisPersistenceService persistenceService;
  private final FileStorageService fileStorageService;

  @Scheduled(cron = "0 30 3 * * ?")
  public void deleteExpiredRepositories() {
    List<ExpiredProjectRepository> expired = persistenceService.findExpiredRepositories(
        LocalDateTime.now()
    );
    expired.forEach(repository -> fileStorageService.deleteFile(repository.repositoryRef()));
    persistenceService.deleteRepositories(expired.stream()
        .map(ExpiredProjectRepository::id)
        .toList());
  }
}
