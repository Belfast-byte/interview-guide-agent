package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    var run = run(decision(), List.of());
    PlannedInterview initialized = mock(PlannedInterview.class);
    PlannedInterview completed = mock(PlannedInterview.class);
    when(persistence.get("session-1")).thenReturn(initialized, completed);

    service.create(run);

    verify(transactions).create(run.creation(), run.plan(), run.decision());
  }

  @Test
  @DisplayName("创建 Agent 返回空首题时在落库前明确拒绝")
  void shouldRejectInvalidInitialQuestionBeforePersistence() {
    AgentDecision invalid = new AgentDecision(
        WorkingMemory.empty(),
        new AgentDecision.Ask(
            "target-0", null,
            new AgentDecision.QuestionDraft("", "理由", List.of(), QuestionType.TEXT, null, null)
        )
    );
    var run = run(invalid, List.of());

    assertThatThrownBy(() -> service.create(run))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("action.ask.question.content");
    verify(transactions, never()).create(run.creation(), run.plan(), run.decision());
  }

  @Test
  @DisplayName("首题采用本次规划提供的 Episode 时通过创建校验并保留采用来源")
  void shouldCreateWithTrustedPlanningHistory() {
    var available = List.of("episode:7", "episode:8");
    var adopted = new InitialQuestionProposal(
        0, "换一个场景验证并发更新。", "参考上次回答", "验证冲突处理", List.of("episode:7"),
        QuestionType.TEXT, null, null).toDecision(plan(), available);
    var run = run(adopted, available);

    service.create(run);

    verify(transactions).create(run.creation(), run.plan(), adopted);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @DisplayName("首题记忆或问题中的伪造 Episode 不会因创建校验放行")
  void shouldRejectUnprovidedEpisode(boolean includeInMemory) {
    var forged = List.of("episode:999");
    var memory = includeInMemory ? WorkingMemory.empty().withAdoptedSources(forged)
        : WorkingMemory.empty();
    var decision = new AgentDecision(memory, new AgentDecision.Ask("target-0", null,
        new AgentDecision.QuestionDraft("请展开说明。", "验证边界", forged, QuestionType.TEXT, null, null)));
    var run = run(decision, List.of("episode:7"));

    assertThatThrownBy(() -> service.create(run))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(includeInMemory ? "workingMemory" : "adoptedSourceRefs");
    verify(transactions, never()).create(run.creation(), run.plan(), run.decision());
  }

  private AdaptiveInterviewCreationService.InitialAgentRun run(AgentDecision decision, List<String> availableRefs) {
    return new AdaptiveInterviewCreationService.InitialAgentRun(
        new AdaptiveSessionCreation(
            null, "session-1", "candidate-1", "JD", "Resume", "provider-1",
            null, null, EVALUATION_SETTINGS),
        plan(),
        decision,
        availableRefs
    );
  }

  private AgentDecision decision() {
    return new InitialQuestionProposal(
        0, "请说明缓存并发更新的冲突处理。", "验证并发边界", "验证冲突处理", List.of(),
        QuestionType.TEXT, null, null).toDecision(plan(), List.of());
  }

  private InterviewPlan plan() {
    return testPlan("session-1", new PlanProposal(List.of(new DimensionProposal(
        "缓存一致性", "并发更新", "CACHE_CONCURRENCY", 2, "java-backend"
    ))));
  }
}
