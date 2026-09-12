package interview.guide.modules.interview.agent.adaptive.tool;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask;
import interview.guide.modules.interview.agent.adaptive.persistence.session.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SessionReadToolsTest {
  private final AdaptiveAgentSessionRepository sessions = mock(AdaptiveAgentSessionRepository.class);
  private final AdaptiveAgentTurnRepository turns = mock(AdaptiveAgentTurnRepository.class);
  private final InterviewMaterialReadTool material = new InterviewMaterialReadTool(sessions);
  private final CodeTaskReadTool code = new CodeTaskReadTool(sessions, turns);

  @Test
  void materialReadsSavedOwnerScopedOriginalWithoutClipping() {
    var session = session();
    String original = "原文\r\n".repeat(4000);
    when(session.resume()).thenReturn(original);
    var result = (ReadToolResult.Success) material.execute(request(Map.of("source", "resume")));
    assertThat(result.data()).containsEntry("text", original).containsOnlyKeys("source", "text");
    verify(sessions).findByIdAndCandidateIdAndTenantId("session", "candidate", "tenant");
    assertThat(result.adoptableSources()).isEmpty();
  }

  @Test
  void rejectsUnknownIdentityArgumentsAndInvalidTurnNumbers() {
    assertThatThrownBy(() -> material.validate(request(Map.of("source", "jd", "sessionId", "other"))))
        .isInstanceOf(ReadToolValidationException.class);
    assertThatThrownBy(() -> material.validate(request(Map.of("source", "JD"))))
        .isInstanceOf(ReadToolValidationException.class);
    for (Object value : List.of(0, -1, 1.5, "1", 1e30)) {
      assertThatThrownBy(() -> code.validate(request(Map.of("turnIndex", value))))
          .isInstanceOf(ReadToolValidationException.class);
    }
    verifyNoInteractions(sessions, turns);
  }

  @Test
  void ownerMismatchStopsBeforeReadingTurns() {
    assertThatThrownBy(() -> code.execute(request(Map.of("turnIndex", 1))))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(turns);
  }

  @Test
  void distinguishesMissingMaterialsAndMissingTurnsFromReadFailure() {
    session();
    assertThat(material.execute(request(Map.of("source", "jd")))).isInstanceOf(ReadToolResult.Empty.class);
    assertThatThrownBy(() -> code.execute(request(Map.of("turnIndex", 1))))
        .isInstanceOf(ReadToolValidationException.class);
    when(sessions.findByIdAndCandidateIdAndTenantId(any(), any(), any()))
        .thenThrow(new IllegalStateException("database unavailable"));
    assertThatThrownBy(() -> material.execute(request(Map.of("source", "jd"))))
        .isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");
  }

  @Test
  void followUpReadsSpecifiedTurnWithoutReplacingCodeOrLeakingGuide() {
    session();
    var selected = mock(AdaptiveAgentTurnEntity.class, RETURNS_DEEP_STUBS);
    var original = mock(AdaptiveAgentTurnEntity.class, RETURNS_DEEP_STUBS);
    when(turns.findBySessionIdAndTurnIndex("session", 3)).thenReturn(Optional.of(selected));
    when(selected.codeRepair().codeTaskTurnIndex()).thenReturn(1);
    when(turns.requireOriginalCodeTask("session", 1)).thenReturn(original);
    when(original.toDomain().turnIndex()).thenReturn(1);
    when(original.toDomain().question()).thenReturn("业务场景");
    when(original.toDomain().codeTask()).thenReturn(new CodeRepairTask("initial\n",
        List.of("要求"), List.of("假设"), new CodeRepairTask.ReviewGuide(List.of(
            new CodeRepairTask.Check("C1", "PRIVATE_DEFECT", "trigger", "acceptance")))));
    when(selected.toDomain().turnIndex()).thenReturn(3);
    when(selected.toDomain().questionType()).thenReturn(CodeRepairTask.QuestionType.TEXT);
    when(selected.toDomain().question()).thenReturn("解释修复");
    when(selected.toDomain().answer()).thenReturn("说明");
    when(selected.workingMemory()).thenReturn(WorkingMemory.empty()
        .withAdoptedSources(List.of("question:1", "question:2")));
    var result = (ReadToolResult.Success) code.execute(request(Map.of("turnIndex", 3)));
    var turn = (CodeTaskReadTool.SelectedTurn) result.data().get("turn");
    assertThat(turn.turnIndex()).isEqualTo(3);
    assertThat(result.data().get("adoptedSourceRefs")).isEqualTo(List.of("question:1", "question:2"));
    assertThat(turn.submittedCode()).isNull();
    assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("reviewGuide", "PRIVATE_DEFECT");
    assertThat(result.adoptableSources()).isEmpty();
  }

  private AdaptiveAgentSessionEntity session() {
    var session = mock(AdaptiveAgentSessionEntity.class);
    when(session.id()).thenReturn("session");
    when(sessions.findByIdAndCandidateIdAndTenantId("session", "candidate", "tenant"))
        .thenReturn(Optional.of(session));
    return session;
  }

  private ReadToolRequest request(Map<String, Object> arguments) {
    var context = mock(AgentContext.class, RETURNS_DEEP_STUBS);
    when(context.session().identity().sessionId()).thenReturn("session");
    when(context.session().identity().owner()).thenReturn(new MemoryOwner("tenant", "candidate"));
    return new ReadToolRequest(context, arguments, Long.MAX_VALUE);
  }
}
