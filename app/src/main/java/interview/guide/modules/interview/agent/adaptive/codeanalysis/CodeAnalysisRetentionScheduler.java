package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import interview.guide.infrastructure.file.FileStorageService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
