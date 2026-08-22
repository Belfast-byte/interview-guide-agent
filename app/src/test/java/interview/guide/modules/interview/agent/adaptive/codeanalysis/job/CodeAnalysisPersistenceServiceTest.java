package interview.guide.modules.interview.agent.adaptive.codeanalysis.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisResult;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnchor;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.claim.ClaimVerification;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.claim.ClaimVerificationRepository;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.claim.ClaimVerificationStatus;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectDigest;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectDigestEntity;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectDigestRepository;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectRepoEntity;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectRepoRepository;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.ScenarioCard;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.ScenarioCardRepository;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.ScenarioTaskType;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CodeAnalysisPersistenceServiceTest {

  @Mock
  private AdaptiveAgentSessionRepository sessionRepository;

  @Mock
  private ProjectRepoRepository repoRepository;

  @Mock
  private AnalysisJobRepository jobRepository;

  @Mock
  private ProjectDigestRepository digestRepository;

  @Mock
  private ClaimVerificationRepository claimRepository;

  @Mock
  private ScenarioCardRepository scenarioRepository;

  private CodeAnalysisPersistenceService service;

  @BeforeEach
  void setUp() {
    service = new CodeAnalysisPersistenceService(
        sessionRepository,
        repoRepository,
        jobRepository,
        digestRepository,
        claimRepository,
        scenarioRepository,
        new ObjectMapper()
    );
  }

  @Nested
  @DisplayName("创建异步分析任务")
  class CreateJob {

    @Test
    @DisplayName("同一会话和 commitHash 复用已存在任务")
    void shouldReuseExistingJob() {
      ProjectRepoEntity repository = new ProjectRepoEntity(
          "repo-1",
          "session-1",
          "tenant-1",
          "s3://repos/one.zip",
          "abc123",
          LocalDateTime.now().plusDays(30)
      );
      AnalysisJobEntity existing = new AnalysisJobEntity("job-1", "session-1", "repo-1");
      when(sessionRepository.existsById("session-1")).thenReturn(true);
      when(repoRepository.findBySessionIdAndCommitHash("session-1", "abc123"))
          .thenReturn(Optional.of(repository));
      when(jobRepository.findTopByRepositoryIdOrderByCreatedAtDesc("repo-1"))
          .thenReturn(Optional.of(existing));

      CodeAnalysisJob job = service.createJob(
          "session-1",
          "tenant-1",
          "s3://repos/one.zip",
          "abc123",
          LocalDateTime.now().plusDays(30)
      );

      assertThat(job.id()).isEqualTo("job-1");
      assertThat(job.status()).isEqualTo(AnalysisJobStatus.PENDING);
    }

    @Test
    @DisplayName("最近任务为 FAILED/TIMED_OUT 终态时新建任务以便重新投递")
    void shouldCreateNewJobWhenLatestIsFailedTerminal() {
      ProjectRepoEntity repository = new ProjectRepoEntity(
          "repo-1",
          "session-1",
          "tenant-1",
          "s3://repos/one.zip",
          "abc123",
          LocalDateTime.now().plusDays(30)
      );
      AnalysisJobEntity failed = new AnalysisJobEntity("job-1", "session-1", "repo-1");
      failed.fail("worker crashed");
      when(sessionRepository.existsById("session-1")).thenReturn(true);
      when(repoRepository.findBySessionIdAndCommitHash("session-1", "abc123"))
          .thenReturn(Optional.of(repository));
      when(jobRepository.findTopByRepositoryIdOrderByCreatedAtDesc("repo-1"))
          .thenReturn(Optional.of(failed));
      when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      CodeAnalysisJob job = service.createJob(
          "session-1",
          "tenant-1",
          "s3://repos/one.zip",
          "abc123",
          LocalDateTime.now().plusDays(30)
      );

      assertThat(job.id()).isNotEqualTo("job-1");
      assertThat(job.status()).isEqualTo(AnalysisJobStatus.PENDING);
      verify(jobRepository).save(any(AnalysisJobEntity.class));
    }
  }

  @Nested
  @DisplayName("任务状态机守卫")
  class StateMachineGuards {

    @Test
    @DisplayName("超时任务的迟到 started 回调被幂等忽略")
    void shouldIgnoreLateStartedOnTimedOutJob() {
      AnalysisJobEntity job = new AnalysisJobEntity("job-1", "session-1", "repo-1");
      job.timeout();
      when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

      service.markRunning("job-1");

      assertThat(job.toDomain().status()).isEqualTo(AnalysisJobStatus.TIMED_OUT);
    }

    @Test
    @DisplayName("已完成任务的迟到 failed 回调不覆盖终态和已完成产物")
    void shouldIgnoreLateFailureOnCompletedJob() {
      AnalysisJobEntity job = new AnalysisJobEntity("job-1", "session-1", "repo-1");
      job.complete(100, 20);
      when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

      service.markFailed("job-1", "worker crashed");

      assertThat(job.toDomain().status()).isEqualTo(AnalysisJobStatus.COMPLETED);
      assertThat(job.toDomain().durationMs()).isEqualTo(100);
      assertThat(job.toDomain().tokenCost()).isEqualTo(20);
    }

    @Test
    @DisplayName("终态任务的迟到结果不写入产物也不翻转状态")
    void shouldIgnoreLateResultOnTerminalJob() {
      AnalysisJobEntity job = new AnalysisJobEntity("job-1", "session-1", "repo-1");
      job.timeout();
      when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
      ProjectDigest digest = new ProjectDigest(
          "digest-1",
          "abc123",
          List.of("Java"),
          List.of(),
          List.of(),
          List.of()
      );

      service.complete("job-1", new CodeAnalysisResult(digest, List.of(), List.of(), 100, 20));

      assertThat(job.toDomain().status()).isEqualTo(AnalysisJobStatus.TIMED_OUT);
      verifyNoInteractions(repoRepository, digestRepository, claimRepository, scenarioRepository);
    }
  }

  @Nested
  @DisplayName("写入结构化分析产物")
  class CompleteJob {

    @Test
    @DisplayName("摘要 commitHash 与仓库快照不一致时拒绝写入")
    void shouldRejectMismatchedCommitHash() {
      AnalysisJobEntity job = new AnalysisJobEntity("job-1", "session-1", "repo-1");
      ProjectRepoEntity repository = new ProjectRepoEntity(
          "repo-1",
          "session-1",
          null,
          "s3://repos/one.zip",
          "expected",
          LocalDateTime.now().plusDays(30)
      );
      ProjectDigest digest = new ProjectDigest(
          "digest-1",
          "other",
          List.of("Java"),
          List.of(),
          List.of(),
          List.of()
      );
      when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
      when(repoRepository.findById("repo-1")).thenReturn(Optional.of(repository));

      assertThatThrownBy(() -> service.complete(
          "job-1",
          new CodeAnalysisResult(digest, List.of(), List.of(), 100, 20)
      )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("commitHash");
    }

    @Test
    @DisplayName("三类产物写入后任务完成并记录成本")
    void shouldStoreAllArtifactsAndCompleteJob() {
      AnalysisJobEntity job = new AnalysisJobEntity("job-1", "session-1", "repo-1");
      ProjectRepoEntity repository = new ProjectRepoEntity(
          "repo-1",
          "session-1",
          null,
          "s3://repos/one.zip",
          "abc123",
          LocalDateTime.now().plusDays(30)
      );
      CodeAnchor anchor = new CodeAnchor("src/OrderService.java", 42);
      ProjectDigest digest = new ProjectDigest(
          "digest-1",
          "abc123",
          List.of("Java"),
          List.of(new ProjectDigest.ProjectModule("order", "订单链路", anchor)),
          List.of(),
          List.of()
      );
      ClaimVerification claim = new ClaimVerification(
          "claim-1",
          "使用缓存优化查询",
          ClaimVerificationStatus.VERIFIED,
          List.of(new ClaimVerification.CodeFact("存在缓存实现", anchor))
      );
      ScenarioCard scenario = new ScenarioCard(
          "scenario-1",
          "缓存失效",
          "解释缓存一致性取舍",
          anchor,
          ScenarioTaskType.EXPLAIN,
          "不修改接口",
          null
      );
      when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
      when(repoRepository.findById("repo-1")).thenReturn(Optional.of(repository));
      when(digestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(claimRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(scenarioRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

      service.complete(
          "job-1",
          new CodeAnalysisResult(digest, List.of(claim), List.of(scenario), 1200, 3400)
      );

      assertThat(job.toDomain().status()).isEqualTo(AnalysisJobStatus.COMPLETED);
      assertThat(job.toDomain().durationMs()).isEqualTo(1200);
      assertThat(job.toDomain().tokenCost()).isEqualTo(3400);
      verify(digestRepository).save(any(ProjectDigestEntity.class));
      verify(claimRepository).saveAll(any());
      verify(scenarioRepository).saveAll(any());
    }
  }
}
