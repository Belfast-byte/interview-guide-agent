package interview.guide.modules.interview.agent.adaptive.application;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testSession;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAnswerAssessmentService.AnswerAssessment;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.session.AdoptedRubricSource;
import interview.guide.modules.interview.agent.adaptive.core.session.AnswerProcessingStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.persistence.session.WorkingMemorySnapshotReader;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import interview.guide.modules.interview.agent.adaptive.runtime.InterviewAgentLoop;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdaptiveAnswerDecisionServiceTest {
  @Test
  @DisplayName("下一题上下文替换回答时保留量规快照和执行元数据")
  void shouldPreserveTurnMetadataInDecisionContext() {
    var plan = testPlan("session-1", new PlanProposal(List.of(
        new DimensionProposal("缓存", "并发", "CACHE", 3, "java-backend"))));
    var rubric = new AdoptedRubricSource("rubric:cache@v1", "cache", "v1", "历史量规正文");
    var turn = new AdaptiveInterviewTurn(1, 0, "问题", "提问理由", null,
        null, null, null, TurnProvenance.initial(), List.of(rubric),
        AnswerProcessingStatus.PROCESSING, "已记录的错误",
        interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType.TEXT, null, null, null, null);
    var history = new AdaptiveInterviewHistory(testSession("session-1", 3).start(),
        "candidate-1", "jd", "resume", "provider", List.of(turn));
    var interview = new PlannedInterview(history, plan);
    var answer = new CandidateAnswer(1, "已接受的回答");
    var assessment = new AnswerAssessment(plan.dimension(0),
        new AssessmentDecision("session-1", 1, DepthLevel.L1, 0.9, "证据不足", List.of(), List.of()), List.of());
    var preparation = mock(AdaptiveAnswerPreparationService.class);
    when(preparation.prepare(interview, answer)).thenReturn(assessment);
    var assembler = mock(ContextAssembler.class);
    var snapshots = mock(WorkingMemorySnapshotReader.class);
    var loop = mock(InterviewAgentLoop.class);
    var service = new AdaptiveAnswerDecisionService(preparation, assembler, snapshots, loop, new TargetBudgetPolicy());

    service.decide(new AdaptiveAnswerDecisionService.AnswerDecisionRequest(
        new MemoryOwner(null, "candidate-1"), interview, answer, Duration.ofSeconds(60), mock(AnswerEventSink.class)));

    var input = org.mockito.ArgumentCaptor.forClass(ContextAssembler.AgentContextInput.class);
    verify(assembler).agent(input.capture());
    var copied = input.getValue().recentTurns().getFirst();
    assertThat(copied.answer()).isEqualTo(answer.content());
    assertThat(copied.assessmentFeedback().depthLevel()).isEqualTo(DepthLevel.L1);
    assertThat(copied.assessmentFeedback().rationale()).isEqualTo("证据不足");
    assertThat(copied).usingRecursiveComparison()
        .ignoringFields("answer", "assessmentFeedback").isEqualTo(turn);
    assertThat(turn.answer()).isNull();
  }

  @Test
  @DisplayName("轮数耗尽仍评估最后回答，允许未覆盖维度且不再组装下一题上下文")
  void shouldFinishAfterLastAssessmentWithoutNextQuestionDependencies() {
    var plan = testPlan("session-1", new PlanProposal(List.of(
        new DimensionProposal("缓存", "并发", "CACHE", 2, "java-backend"),
        new DimensionProposal("数据库", "索引", "INDEX", 2, "java-backend"))));
    var coverage = new CoverageView(4, 0, List.of(
        new CoverageView.TargetCoverage("target-0", plan.dimension(0).target(), 4, DepthLevel.L1, List.of(), List.of()),
        new CoverageView.TargetCoverage("target-1", plan.dimension(1).target(), 0, null, List.of(), List.of())
    ), List.of(), List.of());
    var interview = mock(PlannedInterview.class);
    when(interview.coverage()).thenReturn(coverage);
    var answer = new CandidateAnswer(4, "不知道");
    var assessment = new AnswerAssessment(plan.dimension(0),
        new AssessmentDecision("session-1", 4, DepthLevel.L0, 0.9, "没有可用证据", List.of(), List.of()), List.of());
    var preparation = mock(AdaptiveAnswerPreparationService.class);
    when(preparation.prepare(interview, answer)).thenReturn(assessment);
    var assembler = mock(ContextAssembler.class);
    var snapshots = mock(WorkingMemorySnapshotReader.class);
    var loop = mock(InterviewAgentLoop.class);
    var sink = mock(AnswerEventSink.class);
    var service = new AdaptiveAnswerDecisionService(preparation, assembler, snapshots, loop, new TargetBudgetPolicy());

    var result = service.decide(new AdaptiveAnswerDecisionService.AnswerDecisionRequest(
        new MemoryOwner(null, "candidate-1"), interview, answer, Duration.ofSeconds(60), sink));

    assertThat(result.assessment()).isSameAs(assessment);
    assertThat(result.agentDecision().action()).isInstanceOf(AgentDecision.Finish.class);
    verify(preparation).prepare(interview, answer);
    verify(sink).onStage(AnswerEventSink.AnswerStage.ASSESSING);
    verify(sink, never()).onStage(AnswerEventSink.AnswerStage.GENERATING);
    verifyNoInteractions(assembler, snapshots, loop);
  }
}
