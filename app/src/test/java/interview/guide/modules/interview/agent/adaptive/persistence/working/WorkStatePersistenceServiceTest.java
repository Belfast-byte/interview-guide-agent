package interview.guide.modules.interview.agent.adaptive.persistence.working;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionResultType;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.TargetWorkStatus;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkBudgetType;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkEvidenceRef;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkIssue;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkIssueStatus;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStateOperation;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatchSource;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
    WorkStatePersistenceService.class,
    WorkStateJsonCodec.class,
    WorkStatePersistenceServiceTest.JacksonTestConfig.class
})
class WorkStatePersistenceServiceTest {

  private static final String SESSION_ID = "work-state-session";

  @Autowired
  private WorkStatePersistenceService service;

  @Autowired
  private AdaptiveAgentSessionRepository sessionRepository;

  @Autowired
  private AdaptiveWorkStatePatchRepository patchRepository;

  private InterviewPlan plan;

  @BeforeEach
  void setUp() {
    plan = testPlan(SESSION_ID, new PlanProposal(List.of(new DimensionProposal(
        "缓存", "Redis 持久化", "REDIS", 2, List.of(), "java-backend"))));
    InterviewSessionSettings settings = new InterviewSessionSettings(
        SessionMode.EVALUATION, CandidateLevel.CAMPUS, PracticeScope.none());
    sessionRepository.saveAndFlush(new AdaptiveAgentSessionEntity(
        AdaptiveInterviewSession.create(SESSION_ID, plan.maxTurns(), settings),
        new AdaptiveSessionCreation(
            null, SESSION_ID, "candidate-1", "JD", "Resume", "provider",
            "Provider", "model", settings)
    ));
  }

  @Test
  @DisplayName("初始化与 Patch 在 JSON 往返后保持完整状态")
  void shouldPersistStateAndPatch() {
    InterviewWorkState initial = service.initialize(plan);
    WorkStatePatch patch = patch(initial, "assessment:1", List.of(
        new WorkStateOperation.CompleteAnswer(1),
        new WorkStateOperation.UpdateTargetDepth(initial.activeTargetId(), DepthLevel.L1),
        new WorkStateOperation.ApplyActionResult(ActionResultType.QUESTION, 2, null)
    ));

    InterviewWorkState updated = service.apply(patch);

    assertThat(service.get(SESSION_ID)).isEqualTo(updated);
    assertThat(updated.revision()).isEqualTo(2);
    assertThat(updated.awaitingAnswerTurnIndex()).isEqualTo(2);
    assertThat(patchRepository.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("同一来源只应用一次，不同来源的过期 revision 明确失败")
  void shouldApplySourceOnceAndRejectStaleRevision() {
    InterviewWorkState initial = service.initialize(plan);
    WorkStatePatch first = patch(initial, "tool:1", List.of(
        new WorkStateOperation.SetFocus("AOF 重写边界")
    ));
    InterviewWorkState updated = service.apply(first);

    assertThat(service.apply(first)).isEqualTo(updated);
    assertThatThrownBy(() -> service.apply(patch(initial, "tool:2", List.of(
        new WorkStateOperation.SetFocus("RDB 快照边界")
    )))).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("revision 冲突");
  }

  @Test
  @DisplayName("明确 codec 覆盖全部 Typed Patch 操作")
  void shouldRoundTripEveryOperation() {
    WorkStateJsonCodec codec = new WorkStateJsonCodec(new tools.jackson.databind.ObjectMapper());
    WorkIssue issue = new WorkIssue(
        "issue-1", "target-0", CapabilityTarget.EvidenceMethod.CANDIDATE_ANSWER,
        "AOF", "缺少刷盘策略", WorkIssueStatus.OPEN, null);
    List<WorkStateOperation> operations = List.of(
        new WorkStateOperation.AddEvidenceRef(new WorkEvidenceRef(
            "target-0", "ASSESSMENT", "evidence-1", "使用 AOF")),
        new WorkStateOperation.OpenIssue(issue),
        new WorkStateOperation.CloseIssue("issue-1", WorkIssueStatus.RESOLVED, "已回答"),
        new WorkStateOperation.UpdateTargetDepth("target-0", DepthLevel.L2),
        new WorkStateOperation.SetFocus("刷盘策略"),
        new WorkStateOperation.ConsumeBudget("target-0", WorkBudgetType.TURN),
        new WorkStateOperation.SwitchTarget("target-1", TargetWorkStatus.COMPLETED),
        new WorkStateOperation.SetPendingAction("intent-1"),
        new WorkStateOperation.RetryPendingAction("intent-1", "intent-2"),
        new WorkStateOperation.ApplyActionResult(ActionResultType.QUESTION, 2, "issue-1"),
        new WorkStateOperation.CompleteAnswer(1),
        new WorkStateOperation.FinishSession(TargetWorkStatus.EXHAUSTED)
    );

    assertThat(codec.decodeOperations(codec.encodeOperations(operations)))
        .containsExactlyElementsOf(operations);
  }

  private WorkStatePatch patch(
      InterviewWorkState state,
      String sourceId,
      List<WorkStateOperation> operations
  ) {
    return new WorkStatePatch(
        SESSION_ID + ":" + sourceId,
        SESSION_ID,
        state.revision(),
        state.revision() + 1,
        WorkStatePatchSource.ASSESSMENT,
        sourceId,
        operations
    );
  }

  @TestConfiguration
  static class JacksonTestConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
