package interview.guide.modules.interview.agent.adaptive.tool;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import interview.guide.modules.interview.agent.adaptive.core.context.*;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask;
import interview.guide.modules.interview.agent.adaptive.persistence.session.*;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssessmentReadToolTest {
  private final AdaptiveAgentSessionRepository sessions = mock(AdaptiveAgentSessionRepository.class);
  private final AdaptiveAgentTurnRepository turns = mock(AdaptiveAgentTurnRepository.class);
  private final AdaptiveAgentAssessmentRepository assessments = mock(AdaptiveAgentAssessmentRepository.class);
  private final AdaptiveAgentEvidenceRepository evidences = mock(AdaptiveAgentEvidenceRepository.class);
  private final AssessmentReadTool tool = new AssessmentReadTool(sessions, turns, assessments, evidences);

  @Test
  void pendingAssessmentIsExplicitEmptyAndNeverQueriesTemporaryId() {
    var context = context();
    assertThat(tool.read(context, 2)).isInstanceOf(ReadToolResult.Empty.class);
    verify(assessments).findBySessionIdAndTurnIndex("session", 2);
    verifyNoInteractions(evidences);
  }

  @Test
  void readsGradeReasonAndLocatedEvidenceFromExactlySelectedAssessment() {
    var context = context();
    var fact = mock(AdaptiveAgentAssessmentEntity.class);
    when(fact.id()).thenReturn(19L);
    when(fact.turnIndex()).thenReturn(2);
    when(fact.depthLevel()).thenReturn(DepthLevel.L2);
    when(fact.rationaleSummary()).thenReturn("同次理由");
    when(assessments.findBySessionIdAndTurnIndex("session", 2)).thenReturn(Optional.of(fact));
    var evidence = mock(AdaptiveAgentEvidenceEntity.class);
    when(evidence.id()).thenReturn(7L);
    when(evidence.quoteText()).thenReturn("reserve()");
    when(evidence.quoteLocator()).thenReturn(new SourceQuote.Locator(SourceQuote.Source.SUBMITTED_CODE, 0, 9));
    when(evidences.findByAssessmentIdOrderById(19L)).thenReturn(List.of(evidence));
    var result = (ReadToolResult.Success) tool.read(context, 2);
    assertThat(result.data()).containsEntry("assessmentId", 19L).containsEntry("depthLevel", DepthLevel.L2)
        .containsEntry("rationaleSummary", "同次理由").containsEntry("targetId", "target-0");
    var resultEvidence = (AssessmentReadTool.Evidence) ((List<?>) result.data().get("evidences")).getFirst();
    assertThat(resultEvidence.locator()).isEqualTo(evidence.quoteLocator());
    verify(evidences).findByAssessmentIdOrderById(19L);
    verify(assessments, never()).findTopBySessionIdAndDimensionOrderOrderByTurnIndexDesc(any(), anyInt());
  }

  private AgentContext context() {
    var context = mock(AgentContext.class, RETURNS_DEEP_STUBS);
    when(context.session().identity().sessionId()).thenReturn("session");
    when(context.session().identity().owner()).thenReturn(new MemoryOwner(null, "candidate"));
    var target = mock(CoverageView.TargetCoverage.class, RETURNS_DEEP_STUBS);
    when(target.targetId()).thenReturn("target-0");
    when(context.facts().coverage().targets()).thenReturn(List.of(target));
    var session = mock(AdaptiveAgentSessionEntity.class);
    when(session.id()).thenReturn("session");
    when(sessions.findByIdAndCandidateIdAndTenantIdIsNull("session", "candidate")).thenReturn(Optional.of(session));
    var turn = mock(AdaptiveAgentTurnEntity.class, RETURNS_DEEP_STUBS);
    when(turn.codeRepair().questionType()).thenReturn(CodeRepairTask.QuestionType.TEXT);
    when(turn.codeRepair().codeTaskTurnIndex()).thenReturn(null);
    when(turns.findBySessionIdAndTurnIndex("session", 2)).thenReturn(Optional.of(turn));
    return context;
  }
}
