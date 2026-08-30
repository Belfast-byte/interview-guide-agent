package interview.guide.modules.interview.agent.adaptive.application;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemoryValidator;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveCreationTransactionService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InitialQuestionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecisionValidator;
import interview.guide.modules.interview.skill.InterviewSkillService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdaptiveInterviewCreationServiceTest {

  private final AdaptiveCreationTransactionService transactions =
      mock(AdaptiveCreationTransactionService.class);
  private final AdaptiveInterviewPersistenceService persistence =
      mock(AdaptiveInterviewPersistenceService.class);
  private AdaptiveInterviewCreationService service;

  @BeforeEach
  void setUp() {
    InterviewSkillService skillService = mock(InterviewSkillService.class);
    when(skillService.buildEvaluationReferenceSection("java-backend")).thenReturn("参考资料");
    service = new AdaptiveInterviewCreationService(
        transactions,
        persistence,
        new ContextAssembler(skillService),
        new AgentDecisionValidator(new WorkingMemoryValidator())
    );
  }

  @Test
  @DisplayName("创建链直接校验并发布模型同次返回的计划和首题")
  void shouldPublishInitialDecisionWithoutSecondModelCall() {
    var run = run(decision());
    PlannedInterview initialized = mock(PlannedInterview.class);
    PlannedInterview completed = mock(PlannedInterview.class);
    when(persistence.get("session-1")).thenReturn(initialized, completed);

    service.initialize(run);
    service.complete(run);

    verify(transactions).initialize(run.creation(), run.plan());
    verify(transactions).publishFirstTurn(
        new AdaptiveCreationTransactionService.InitialTurnCommit(
            "session-1", run.plan(), run.decision()));
  }

  @Test
  @DisplayName("创建 Agent 返回空首题时在落库前明确拒绝")
  void shouldRejectInvalidInitialQuestionBeforePersistence() {
    AgentDecision invalid = new AgentDecision(
        WorkingMemory.empty(),
        new AgentDecision.Ask(
            "target-0", null,
            new AgentDecision.QuestionDraft("", "理由", List.of())
        )
    );
    var run = run(invalid);

    assertThatThrownBy(() -> service.initialize(run))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("action.ask.question.content");
    verify(transactions, never()).initialize(run.creation(), run.plan());
  }

  private AdaptiveInterviewCreationService.InitialAgentRun run(AgentDecision decision) {
    return new AdaptiveInterviewCreationService.InitialAgentRun(
        new AdaptiveSessionCreation(
            null, "session-1", "candidate-1", "JD", "Resume", "provider-1",
            null, null, EVALUATION_SETTINGS),
        plan(),
        decision
    );
  }

  private AgentDecision decision() {
    return new InitialQuestionProposal(
        0, "请说明缓存并发更新的冲突处理。", "验证并发边界", "验证冲突处理"
    ).toDecision(plan());
  }

  private InterviewPlan plan() {
    return testPlan("session-1", new PlanProposal(List.of(new DimensionProposal(
        "缓存一致性", "并发更新", "CACHE_CONCURRENCY", 2, "java-backend"
    ))));
  }
}
