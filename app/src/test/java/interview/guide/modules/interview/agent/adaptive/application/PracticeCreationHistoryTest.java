package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemoryValidator;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask;
import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeQueryService.EpisodeView;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMemoryService.PracticePlanningMemory;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMemoryService;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveCreationTransactionService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InitialQuestionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningAgent;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningRequest;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningTaxonomy;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecisionValidator;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.llmprovider.service.CandidateChatProvider;
import interview.guide.modules.llmprovider.service.CandidateLlmProviderService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PracticeCreationHistoryTest {

  private static final TopicKey TOPIC = new TopicKey("java-backend", "CONCURRENCY");
  private static final MemoryOwner OWNER = new MemoryOwner("tenant-a", "candidate-1");
  private static final PracticeScope SCOPE = new PracticeScope(List.of(TOPIC));
  private static final String AVAILABLE_EPISODE = "episode:7";
  private final CandidateLlmProviderService providers = mock(CandidateLlmProviderService.class);
  private final PracticeMemoryService memory = mock(PracticeMemoryService.class);
  private final PlanningAgent planner = mock(PlanningAgent.class);
  private final AdaptiveCreationTransactionService transactions = mock(AdaptiveCreationTransactionService.class);

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @DisplayName("练习入口将 owner 历史贯穿规划与创建校验，只发布合法历史来源")
  void shouldCarryPlanningHistoryThroughCreation(boolean trusted) {
    var episode = mock(EpisodeView.class);
    when(episode.reference()).thenReturn(AVAILABLE_EPISODE);
    var history = new PracticePlanningMemory(List.of(
        new PracticePlanningMemory.TopicHistory(TOPIC, List.of(episode))));
    when(memory.planning(OWNER, SCOPE)).thenReturn(history);
    String adopted = trusted ? AVAILABLE_EPISODE : "episode:999";
    when(planner.propose(any(), eq("provider-1"))).thenAnswer(invocation -> {
      var request = invocation.getArgument(0,
          PlanningRequest.class);
      assertThat(request.practiceMemory()).isEqualTo(history);
      return proposal(adopted);
    });
    var application = application();
    var command = new TenantInterviewCreationCommand(OWNER.tenantId(), OWNER.candidateId(),
        "JD", "Resume", "provider-1",
        new InterviewSessionSettings(SessionMode.PRACTICE, CandidateLevel.CAMPUS, SCOPE));

    if (!trusted) {
      assertThatThrownBy(() -> application.createForTenant(command))
          .isInstanceOf(BusinessException.class).hasMessageContaining("Episode 引用不在提供的历史中");
      verifyNoInteractions(transactions);
      return;
    }
    application.createForTenant(command);
    verify(memory).planning(OWNER, SCOPE);
    var decision = ArgumentCaptor.forClass(AgentDecision.class);
    verify(transactions).create(any(), any(), decision.capture());
    assertThat(decision.getValue().workingMemory().deliberation().adoptedObservationRefs())
        .containsExactly(AVAILABLE_EPISODE);
    assertThat(((AgentDecision.Ask) decision.getValue().action()).question().adoptedSourceRefs())
        .containsExactly(AVAILABLE_EPISODE);
  }

  @Test
  void shouldRejectTextBeforePublishingCandidateFirstQuestion() {
    var candidate = UUID.randomUUID();
    when(providers.resolveChatProvider(candidate, "provider-1")).thenReturn(
        new CandidateChatProvider("provider-1", "Provider", "model"));
    when(planner.propose(any(), eq("provider-1"))).thenAnswer(invocation -> {
      var request = invocation.getArgument(0,
          PlanningRequest.class);
      assertThat(request.codeRepairFirst()).isTrue();
      return proposal(AVAILABLE_EPISODE);
    });
    var command = new CandidateInterviewCreationCommand(candidate, "JD", "Resume", "provider-1",
        new InterviewSessionSettings(SessionMode.EVALUATION, CandidateLevel.CAMPUS,
            new PracticeScope(List.of())));
    assertThatThrownBy(() -> application().createForCandidate(command))
        .isInstanceOf(BusinessException.class).hasMessageContaining("Java 代码改错首题");
    verifyNoInteractions(transactions);
  }

  @ParameterizedTest
  @EnumSource(SessionMode.class)
  void candidateCreationPublishesCodeFirstUsingOriginalMaterials(SessionMode mode) {
    var candidate = UUID.randomUUID();
    when(providers.resolveChatProvider(candidate, "provider-1")).thenReturn(
        new CandidateChatProvider("provider-1", "Provider", "model"));
    if (mode == SessionMode.PRACTICE) {
      when(memory.planning(new MemoryOwner(null, candidate.toString()), SCOPE))
          .thenReturn(new PracticePlanningMemory(List.of()));
    }
    var task = new CodeRepairTask(
        "void reserve() {}", List.of("不能超卖"), List.of("共享数据库"),
        new CodeRepairTask.ReviewGuide(List.of(
            new CodeRepairTask.Check(
                "C1", "未扣库存", "下单", "原子扣减"))));
    when(planner.propose(any(), eq("provider-1"))).thenAnswer(invocation -> {
      var request = invocation.getArgument(0,
          PlanningRequest.class);
      assertThat(request.codeRepairFirst()).isTrue();
      assertThat(request.context().jd()).isEqualTo("原始 JD");
      assertThat(request.context().resume()).isEqualTo("原始简历");
      assertThat(request.context().mode()).isEqualTo(mode);
      return new PlanProposal(proposal(AVAILABLE_EPISODE).dimensions(),
          new InitialQuestionProposal(0, "修复库存扣减", "验证并发", "原子更新", List.of(),
              QuestionType.CODE_REPAIR, task, null));
    });
    application().createForCandidate(new CandidateInterviewCreationCommand(candidate, "原始 JD", "原始简历",
        "provider-1", new InterviewSessionSettings(mode, CandidateLevel.CAMPUS,
            mode == SessionMode.PRACTICE ? SCOPE : new PracticeScope(List.of()))));
    var decision = ArgumentCaptor.forClass(AgentDecision.class);
    verify(transactions).create(any(), any(), decision.capture());
    var question = ((AgentDecision.Ask) decision.getValue().action()).question();
    assertThat(question.questionType()).isEqualTo(QuestionType.CODE_REPAIR);
    assertThat(question.codeTask()).isEqualTo(task);
  }

  private PlanProposal proposal(String adopted) {
    return new PlanProposal(List.of(new DimensionProposal(
        "并发控制", "并发更新", TOPIC.focusId(), 2, TOPIC.skillId())),
        new InitialQuestionProposal(0, "换场景验证并发。", "参考原回答", "验证边界", List.of(adopted), QuestionType.TEXT, null, null));
  }

  private AdaptiveInterviewApplicationService application() {
    var persistence = mock(AdaptiveInterviewPersistenceService.class);
    var skills = mock(InterviewSkillService.class);
    when(skills.buildEvaluationReferenceSection(anyString())).thenReturn("评估参考");
    var assembler = new ContextAssembler(skills, new interview.guide.modules.interview.agent.adaptive.runtime.AdaptiveAgentRuntimeConfiguration.QueryTools(List.of()));
    var creation = new AdaptiveInterviewCreationService(transactions, persistence, assembler,
        new AgentDecisionValidator(new WorkingMemoryValidator()));
    return new AdaptiveInterviewApplicationService(persistence, mock(AdaptiveAgentTelemetry.class),
        planner, assembler, mock(PlanningTaxonomy.class), memory,
        mock(AlgorithmInterviewTelemetry.class), providers,
        mock(AdaptiveInterviewCreationTaskRunner.class), creation, new AdaptiveAgentProperties(),
        mock(AdaptiveAnswerProgressionService.class));
  }
}
