package interview.guide.modules.interview.agent.adaptive.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeQueryService;
import interview.guide.modules.interview.agent.adaptive.memory.episode.exposure.QuestionExposureRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

class NativeInterviewToolsTest {
  private final AdaptiveAgentSessionRepository sessions = mock(AdaptiveAgentSessionRepository.class);
  private final AdaptiveAgentTurnRepository turns = mock(AdaptiveAgentTurnRepository.class);

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"turnIndex\":null}", "{\"turnIndex\":\"1\"}",
      "{\"turnIndex\":1.5}", "{\"turnIndex\":2147483648}", "{\"turnIndex\":-1}",
      "{\"turnIndex\":0}", "{\"turnIndex\":1,\"owner\":\"other\"}",
      "{\"turnIndex\":1} {}", "[]"})
  void rejectsMalformedArgumentsBeforeAnyRepositoryRead(String input) {
    var scope = scope("code_task_read");
    String result = callback(new CodeTaskReadTool(sessions, turns)).call(input, new ToolContext(scope.values()));
    assertThat(result).contains("VALIDATION_REJECTION");
    verifyNoInteractions(sessions, turns);
  }

  @Test
  void ownershipFailurePropagatesThroughNativeMethodBinding() {
    var scope = scope("code_task_read");
    assertThatThrownBy(() -> callback(new CodeTaskReadTool(sessions, turns))
        .call("{\"turnIndex\":1}", new ToolContext(scope.values())))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(turns);
    assertThat(scope.observations()).isEmpty();
  }

  @Test
  void disallowedToolCannotReadEvenWithARegisteredCallback() {
    var scope = scope("assessment_read");
    assertThat(callback(new CodeTaskReadTool(sessions, turns))
        .call("{\"turnIndex\":1}", new ToolContext(scope.values())))
        .contains("VALIDATION_REJECTION", "白名单");
    verifyNoInteractions(sessions, turns);
  }

  @Test
  void evaluationNeverRecallsHistoricalAbilityViaNativeMethod() {
    var episodes = mock(EpisodeQueryService.class);
    var exposures = mock(QuestionExposureRepository.class);
    var context = context("memory_recall");
    when(context.session().mode()).thenReturn(SessionMode.EVALUATION);
    var target = mock(CoverageView.TargetCoverage.class, RETURNS_DEEP_STUBS);
    when(context.facts().coverage().targets()).thenReturn(List.of(target));
    when(target.targetId()).thenReturn("target-0");
    var scope = new InterviewToolContext(context, Long.MAX_VALUE, 2);
    String result = callback(new MemoryRecallTool(episodes, exposures))
        .call("{\"targetId\":\"target-0\"}", new ToolContext(scope.values()));
    assertThat(result).contains("TOOL_SUCCESS", "recentQuestions").doesNotContain("episodes");
    verifyNoInteractions(episodes);
    assertThat(scope.observations()).hasSize(1);
  }

  @Test
  void deadlineAndClosedRequestPreventLateObservations() {
    var scope = scope("code_task_read");
    scope.close();
    assertThatThrownBy(() -> scope.observe("code_task_read", new ReadToolResult.Empty("late")))
        .isInstanceOf(BusinessException.class);
    assertThat(scope.observations()).isEmpty();
    var expired = new InterviewToolContext(context("code_task_read"), System.nanoTime() - 1, 2);
    assertThatThrownBy(() -> callback(new CodeTaskReadTool(sessions, turns))
        .call("{\"turnIndex\":1}", new ToolContext(expired.values())))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(sessions, turns);
  }

  @Test
  void optionalDifficultyStillRejectsExplicitNullAndWrongType() {
    var tool = new QuestionSearchTool(mock(org.springframework.ai.vectorstore.VectorStore.class),
        mock(interview.guide.modules.knowledgebase.repository.KnowledgeBaseQuestionRepository.class),
        new ToolProperties());
    for (String value : List.of("null", "12", "true", "[]")) {
      var scope = scope("question_search");
      assertThat(callback(tool).call("{\"query\":\"q\",\"difficulty\":" + value + "}",
          new ToolContext(scope.values()))).contains("VALIDATION_REJECTION");
    }
  }

  @Test
  void rejectsDuplicateQueriesAndBudgetOverflowBeforeSecondRead() {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var scope = new InterviewToolContext(context("probe"), Long.MAX_VALUE, 1);
    var callback = callback(new Probe(() -> calls.incrementAndGet()));
    var trusted = new ToolContext(scope.values());
    assertThat(callback.call("{\"query\":\"one\"}", trusted)).contains("TOOL_EMPTY");
    assertThat(callback.call("{\"query\":\"one\"}", trusted)).contains("VALIDATION_REJECTION");
    assertThatThrownBy(() -> callback.call("{\"query\":\"two\"}", trusted))
        .isInstanceOf(BusinessException.class).hasMessageContaining("上限");
    assertThat(calls).hasValue(1);
  }

  @Test
  void executionErrorsAreExplicitAndLateResultsCannotRegister() {
    var scope = scope("probe");
    var error = callback(new Probe(() -> { throw new IllegalStateException("database failed"); }));
    assertThat(error.call("{\"query\":\"one\"}", new ToolContext(scope.values())))
        .contains("TOOL_ERROR").doesNotContain("database failed");
    for (boolean fail : List.of(false, true)) {
      var lateScope = scope("probe");
      var late = callback(new Probe(() -> {
        lateScope.close();
        if (fail) throw new IllegalStateException("late failure");
      }));
      assertThatThrownBy(() -> late.call("{\"query\":\"one\"}", new ToolContext(lateScope.values())))
          .isInstanceOf(BusinessException.class);
      assertThat(lateScope.observations()).isEmpty();
    }
  }

  static class Probe {
    private final Runnable operation;
    Probe(Runnable operation) { this.operation = operation; }
    @org.springframework.ai.tool.annotation.Tool(name = "probe", description = "test read")
    public interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation read(
        String query, ToolContext context) {
      operation.run();
      return InterviewToolContext.from(context).observe("probe", new ReadToolResult.Empty("empty"));
    }
  }

  private ToolCallback callback(Object tool) {
    return new InterviewToolCallback(ToolCallbacks.from(tool)[0], true);
  }

  private InterviewToolContext scope(String name) {
    return new InterviewToolContext(context(name), Long.MAX_VALUE, 2);
  }

  private AgentContext context(String name) {
    var context = mock(AgentContext.class, RETURNS_DEEP_STUBS);
    when(context.facts().allowedReadTools()).thenReturn(List.of(name));
    when(context.session().identity().owner()).thenReturn(new MemoryOwner("tenant", "candidate"));
    when(context.session().identity().sessionId()).thenReturn("session");
    return context;
  }
}
