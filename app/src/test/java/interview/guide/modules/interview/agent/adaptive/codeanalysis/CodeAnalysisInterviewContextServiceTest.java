package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.codeanalysis.claim.ClaimVerification;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.claim.ClaimVerificationEntity;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.claim.ClaimVerificationRepository;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.claim.ClaimVerificationStatus;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.AnalysisJobEntity;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisPersistenceService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectDigest;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectDigestEntity;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectDigestRepository;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.ScenarioCard;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.ScenarioCardEntity;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.ScenarioCardRepository;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario.ScenarioTaskType;
import interview.guide.modules.interview.agent.adaptive.core.context.ProjectInterviewContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CodeAnalysisInterviewContextServiceTest {

  @Mock
  private CodeAnalysisPersistenceService persistenceService;

  @Mock
  private ProjectDigestRepository digestRepository;

  @Mock
  private ClaimVerificationRepository claimRepository;

  @Mock
  private ScenarioCardRepository scenarioRepository;

  private CodeAnalysisInterviewContextService service;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    service = new CodeAnalysisInterviewContextService(
        persistenceService,
        digestRepository,
        claimRepository,
        scenarioRepository,
        objectMapper
    );
  }

  @Test
  @DisplayName("没有已完成分析时保持简历问答降级路径")
  void shouldReturnEmptyWithoutCompletedAnalysis() {
    when(persistenceService.findLatestCompletedJob("session-1"))
        .thenReturn(Optional.empty());

    assertThat(service.findForSession("session-1")).isEmpty();
  }

  @Test
  @DisplayName("已完成分析转换为主张和场景上下文并保留真实锚点")
  void shouldMapCompletedArtifacts() throws Exception {
    AnalysisJobEntity job = new AnalysisJobEntity("job-1", "session-1", "repo-1");
    job.complete(100, 20);
    CodeAnchor anchor = new CodeAnchor("src/OrderCache.java", 42);
    ProjectDigest digest = new ProjectDigest(
        "digest-1",
        "abc123",
        List.of("Java"),
        List.of(),
        List.of(),
        List.of()
    );
    ClaimVerification claim = new ClaimVerification(
        "claim-1",
        "实现了订单缓存",
        ClaimVerificationStatus.CONTRADICTED,
        List.of(new ClaimVerification.CodeFact("实际直接调用 SDK", anchor))
    );
    ScenarioCard scenario = new ScenarioCard(
        "scenario-1",
        "缓存一致性",
        "订单更新后存在短暂旧值",
        anchor,
        ScenarioTaskType.EXPLAIN,
        "保持接口不变",
        null
    );
    when(persistenceService.findLatestCompletedJob("session-1"))
        .thenReturn(Optional.of(job.toDomain()));
    when(digestRepository.findByRepositoryId("repo-1")).thenReturn(Optional.of(
        new ProjectDigestEntity(
            "digest-1",
            "repo-1",
            "abc123",
            objectMapper.writeValueAsString(digest)
        )
    ));
    when(claimRepository.findByRepositoryIdOrderByClaimId("repo-1")).thenReturn(List.of(
        new ClaimVerificationEntity(
            "claim-1",
            "repo-1",
            objectMapper.writeValueAsString(claim)
        )
    ));
    when(scenarioRepository.findByRepositoryIdOrderByScenarioId("repo-1")).thenReturn(List.of(
        new ScenarioCardEntity(
            "scenario-1",
            "repo-1",
            objectMapper.writeValueAsString(scenario)
        )
    ));

    ProjectInterviewContext context = service.findForSession("session-1").orElseThrow();

    assertThat(context.digestId()).isEqualTo("digest-1");
    assertThat(context.claims().getFirst().status()).isEqualTo("CONTRADICTED");
    assertThat(context.claims().getFirst().codeFacts().getFirst().anchor())
        .isEqualTo("src/OrderCache.java:42");
    assertThat(context.scenarios().getFirst().anchor())
        .isEqualTo("src/OrderCache.java:42");
  }
}
