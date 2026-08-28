package interview.guide.modules.interview.agent.adaptive.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.core.action.ToolCallAction;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntent;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentKey;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentOutcome;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionResultType;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionTarget;
import interview.guide.modules.interview.agent.adaptive.core.intent.ToolActionPayload;
import interview.guide.modules.interview.agent.adaptive.core.intent.ToolCallSpec;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.persistence.intent.ActionIntentPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.intent.ActionIntentTransactionService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.working.WorkStatePersistenceService;
import interview.guide.modules.interview.agent.adaptive.role.AgentRoleRegistry;
import interview.guide.modules.interview.agent.adaptive.runtime.BoundedActionRuntime;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecution;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecutionOutcome;
import interview.guide.modules.interview.agent.adaptive.tool.ToolGateway;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ToolActionIntentTest {

  private final BoundedActionRuntime runtime = mock(BoundedActionRuntime.class);
  private final AgentRoleRegistry roles = mock(AgentRoleRegistry.class);
  private final ToolGateway tools = mock(ToolGateway.class);
  private final ActionIntentPersistenceService intents =
      mock(ActionIntentPersistenceService.class);
  private final ActionIntentTransactionService transactions =
      mock(ActionIntentTransactionService.class);
  private final WorkStatePersistenceService workStates =
      mock(WorkStatePersistenceService.class);
  private final AdaptiveInterviewPersistenceService interviews =
      mock(AdaptiveInterviewPersistenceService.class);
  private final QuestionNoveltyService novelty = mock(QuestionNoveltyService.class);
  private final ReActRequest request = mock(ReActRequest.class);
  private final InterviewWorkState state = mock(InterviewWorkState.class);
  private ActionIntentExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new ActionIntentExecutor(
        runtime, roles, tools, intents, transactions, workStates, interviews, novelty);
    when(state.sessionId()).thenReturn("session-1");
    when(state.revision()).thenReturn(2L);
    when(state.activeTargetId()).thenReturn("target-0");
    when(state.activeOpenIssues()).thenReturn(List.of());
  }

  @Test
  @DisplayName("CALL_TOOL Intent 进入执行态后使用持久幂等键调用工具")
  void shouldExecuteToolWithIntentKey() {
    ActionIntent planned = planned();
    ActionIntent executing = planned.start(now());
    ActionIntent succeeded = succeeded(executing);
    ToolExecution result = toolResult();
    when(intents.start("intent-1")).thenReturn(executing);
    when(tools.execute(eq(request), any(), eq("intent-1"))).thenReturn(result);
    when(intents.get("intent-1")).thenReturn(succeeded);
    when(transactions.toolExecution("intent-1")).thenReturn(result);
    when(workStates.get("session-1")).thenReturn(state);

    InterviewWorkState updated = executor.executeTool(
        new ToolIntentExecution(planned, request));

    InOrder order = inOrder(intents, tools, transactions);
    order.verify(intents).start("intent-1");
    order.verify(tools).execute(eq(request), any(ToolCallAction.class), eq("intent-1"));
    order.verify(transactions).completeTool("session-1", "intent-1", result);
    order.verify(intents).apply(eq("intent-1"), any());
    assertThat(updated).isSameAs(state);
  }

  @Test
  @DisplayName("SUCCEEDED CALL_TOOL 恢复只补 Patch，不重复调用工具")
  void shouldOnlyApplySucceededToolResult() {
    ActionIntent succeeded = succeeded(planned().start(now()));
    ToolExecution result = toolResult();
    when(transactions.toolExecution("intent-1")).thenReturn(result);
    when(workStates.get("session-1")).thenReturn(state);

    executor.applySucceededTool(succeeded);

    verify(tools, never()).execute(any(), any(), any());
    verify(transactions, never()).completeTool(any(), any(), any());
    verify(intents).apply(eq("intent-1"), any());
  }

  @Test
  @DisplayName("超时 EXECUTING CALL_TOOL 恢复时复用原幂等键")
  void shouldRecoverExecutingToolWithSameKey() {
    ActionIntent executing = planned().start(now());
    ActionIntent restarted = executing.restart(now());
    ActionIntent succeeded = succeeded(restarted);
    ToolExecution result = toolResult();
    when(intents.restart("intent-1")).thenReturn(restarted);
    when(tools.execute(eq(request), any(), eq("intent-1"))).thenReturn(result);
    when(intents.get("intent-1")).thenReturn(succeeded);
    when(transactions.toolExecution("intent-1")).thenReturn(result);
    when(workStates.get("session-1")).thenReturn(state);

    executor.recoverTool(new ToolIntentExecution(executing, request));

    verify(tools).execute(eq(request), any(), eq("intent-1"));
    verify(transactions).completeTool("session-1", "intent-1", result);
    verify(intents).apply(eq("intent-1"), any());
  }

  private ActionIntent planned() {
    return ActionIntent.planned(
        new ActionIntentKey("intent-1", "session-1", 1),
        new ToolActionPayload(
            new ActionTarget("target-0", "issue-1", 1),
            new ToolCallSpec("rubric_lookup", Map.of("dimension", "Redis"), "读取量规"),
            "intent-1"
        ),
        now()
    );
  }

  private ActionIntent succeeded(ActionIntent executing) {
    return executing.succeed(
        ActionIntentOutcome.succeeded(ActionResultType.TOOL_RESULT, "intent-1"), now());
  }

  private ToolExecution toolResult() {
    return new ToolExecution(
        "intent-1", "rubric_lookup", "读取量规", "INTERVIEWER", 1,
        "keys=[dimension]", "量规已返回", "result-1", "{}",
        ToolExecutionOutcome.COMPLETED, 10
    );
  }

  private LocalDateTime now() {
    return LocalDateTime.of(2026, 8, 28, 8, 0);
  }
}
