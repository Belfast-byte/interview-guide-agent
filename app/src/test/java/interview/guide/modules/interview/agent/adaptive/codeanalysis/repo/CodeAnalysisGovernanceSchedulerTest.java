package interview.guide.modules.interview.agent.adaptive.codeanalysis.repo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisPersistenceService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisProperties;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisTimeoutScheduler;
import interview.guide.modules.interview.agent.adaptive.observability.CodeAnalysisTelemetry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CodeAnalysisGovernanceSchedulerTest {

  @Mock
  private CodeAnalysisPersistenceService persistenceService;

  @Mock
  private FileStorageService fileStorageService;

  @Mock
  private CodeAnalysisTelemetry telemetry;

  @Test
  @DisplayName("过期快照先从对象存储删除再级联删除事实")
  void shouldDeleteStorageBeforeFacts() {
    when(persistenceService.findExpiredRepositories(any())).thenReturn(List.of(
        new ExpiredProjectRepository("repo-1", "repos/one.zip")
    ));
    CodeAnalysisRetentionScheduler scheduler = new CodeAnalysisRetentionScheduler(
        persistenceService,
        fileStorageService
    );

    scheduler.deleteExpiredRepositories();

    InOrder order = inOrder(fileStorageService, persistenceService);
    order.verify(fileStorageService).deleteFile("repos/one.zip");
    order.verify(persistenceService).deleteRepositories(List.of("repo-1"));
  }

  @Test
  @DisplayName("超时任务统一标记降级并记录数量")
  void shouldMarkAndMeasureTimedOutJobs() {
    when(persistenceService.timeoutCreatedBefore(any())).thenReturn(2);
    CodeAnalysisTimeoutScheduler scheduler = new CodeAnalysisTimeoutScheduler(
        persistenceService,
        new CodeAnalysisProperties(),
        telemetry
    );

    scheduler.timeoutStaleJobs();

    verify(telemetry).jobsTimedOut(2);
  }
}
