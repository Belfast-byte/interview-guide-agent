package interview.guide.modules.interview.agent.adaptive.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntent;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentKey;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentOutcome;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionResultType;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionTarget;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionContext;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionPayload;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStateOperation;
import interview.guide.modules.interview.agent.adaptive.core.session.NextTurnProvenanceDraft;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionIdentity;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionNoveltyDecision;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionPublication;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionReview;
import interview.guide.modules.interview.agent.adaptive.persistence.intent.ActionIntentPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.intent.ActionIntentTransactionService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAskIntentCompletion;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.working.WorkStatePersistenceService;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.role.AgentRole;
import interview.guide.modules.interview.agent.adaptive.role.AgentRoleDefinition;
import interview.guide.modules.interview.agent.adaptive.role.AgentRoleRegistry;
import interview.guide.modules.interview.agent.adaptive.runtime.BoundedActionRuntime;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import interview.guide.modules.interview.agent.adaptive.runtime.RuntimeDeadline;
import interview.guide.modules.interview.agent.adaptive.tool.ToolGateway;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class AskActionIntentRecoveryTest {

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
  private final PlannedInterview interview = mock(PlannedInterview.class);
  private ActionIntentExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new ActionIntentExecutor(
        runtime, roles, tools, intents, transactions, workStates, interviews, novelty);
    when(request.role()).thenReturn(AgentRole.INTERVIEWER);
    when(roles.get(AgentRole.INTERVIEWER)).thenReturn(new AgentRoleDefinition(
        AgentRole.INTERVIEWER, Duration.ofSeconds(10), Set.of()));
    when(state.sessionId()).thenReturn("session-1");
    when(state.revision()).thenReturn(2L);
    when(interviews.get("session-1")).thenReturn(interview);
  }

  @Test
  @DisplayName("ASK Intent 先进入执行态，问题落库后才应用结果 Patch")
  void shouldPersistQuestionBeforeApplyingResult() {
    ActionIntent planned = planned();
    ActionIntent executing = planned.start(now());
    ActionIntent succeeded = executing.succeed(
        ActionIntentOutcome.succeeded(ActionResultType.QUESTION, "turn:2"), now());
    RespondAction question = RespondAction.ask("Redis RDB 如何触发？", "验证持久化机制");
    when(intents.start("intent-1")).thenReturn(executing);
    when(runtime.proposeBefore(eq(request), any(RuntimeDeadline.class), any()))
        .thenReturn(question);
    when(novelty.review(request, question)).thenReturn(accepted(question));
    when(intents.get("intent-1")).thenReturn(succeeded);
    when(workStates.get("session-1")).thenReturn(state);

    @SuppressWarnings("unchecked")
    Consumer<String> sink = mock(Consumer.class);
    PlannedInterview result = executor.executeAsk(
        new AskIntentExecution(planned, request, sink));

    InOrder order = inOrder(intents, runtime, transactions, sink);
    order.verify(intents).start("intent-1");
    order.verify(runtime).proposeBefore(eq(request), any(RuntimeDeadline.class), any());
    order.verify(transactions).completeAsk(
        new AdaptiveAskIntentCompletion("session-1", "intent-1", publication(question)));
    order.verify(sink).accept(question.content());
    order.verify(intents).apply(eq("intent-1"), any());
    assertThat(result).isSameAs(interview);
  }

  @Test
  @DisplayName("SUCCEEDED ASK 恢复只补 WorkState，不再次生成或展示问题")
  void shouldOnlyApplySucceededQuestion() {
    ActionIntent succeeded = planned().start(now()).succeed(
        ActionIntentOutcome.succeeded(ActionResultType.QUESTION, "turn:2"), now());
    when(workStates.get("session-1")).thenReturn(state);

    executor.applySucceededAsk(succeeded);

    verify(runtime, never()).proposeBefore(any(), any(), any());
    verify(transactions, never()).completeAsk(any());
    ArgumentCaptor<interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch>
        patch = ArgumentCaptor.forClass(
            interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch.class);
    verify(intents).apply(eq("intent-1"), patch.capture());
    assertThat(patch.getValue().operations()).containsExactly(
        new WorkStateOperation.ApplyActionResult(ActionResultType.QUESTION, 2, "issue-1"));
  }

  @Test
  @DisplayName("超时 EXECUTING ASK 使用原 Intent 重新生成问题")
  void shouldRecoverExecutingQuestionWithSameIntent() {
    ActionIntent executing = planned().start(now());
    ActionIntent restarted = executing.restart(now());
    ActionIntent succeeded = restarted.succeed(
        ActionIntentOutcome.succeeded(ActionResultType.QUESTION, "turn:2"), now());
    when(intents.restart("intent-1")).thenReturn(restarted);
    RespondAction question = RespondAction.ask(
        "Redis AOF 重写如何保证正确？", "验证持久化机制");
    when(runtime.proposeBefore(eq(request), any(RuntimeDeadline.class), any()))
        .thenReturn(question);
    when(novelty.review(request, question)).thenReturn(accepted(question));
    when(intents.get("intent-1")).thenReturn(succeeded);
    when(workStates.get("session-1")).thenReturn(state);

    executor.recoverAsk(new AskIntentExecution(executing, request, null));

    verify(intents).restart("intent-1");
    verify(transactions).completeAsk(any());
    verify(intents).apply(eq("intent-1"), any());
  }

  @Test
  @DisplayName("重复问题在同一 deadline 内换场景后才发布")
  void shouldRewriteRepeatedQuestionWithinSameDeadline() {
    ActionIntent planned = planned();
    ActionIntent executing = planned.start(now());
    ActionIntent succeeded = executing.succeed(
        ActionIntentOutcome.succeeded(ActionResultType.QUESTION, "turn:2"), now());
    ReActRequest rewriteRequest = mock(ReActRequest.class);
    RespondAction first = RespondAction.ask("RDB 和 AOF 如何取舍？", "验证持久化");
    RespondAction rewritten = RespondAction.ask(
        "凌晨主机断电后，你如何判断 Redis 最多丢多少数据？", "换故障场景验证");
    QuestionReview repeated = new QuestionReview(
        QuestionNoveltyDecision.Type.REWRITE, publication(first), List.of());
    QuestionReview accepted = accepted(rewritten);
    when(intents.start("intent-1")).thenReturn(executing);
    when(runtime.proposeBefore(eq(request), any(RuntimeDeadline.class), any()))
        .thenReturn(first);
    when(novelty.review(request, first)).thenReturn(repeated);
    when(novelty.rewriteRequest(request, repeated)).thenReturn(rewriteRequest);
    when(runtime.proposeBefore(eq(rewriteRequest), any(RuntimeDeadline.class), any()))
        .thenReturn(rewritten);
    when(novelty.review(rewriteRequest, rewritten)).thenReturn(accepted);
    when(intents.get("intent-1")).thenReturn(succeeded);
    when(workStates.get("session-1")).thenReturn(state);

    executor.executeAsk(new AskIntentExecution(planned, request, null));

    ArgumentCaptor<RuntimeDeadline> deadlines = ArgumentCaptor.forClass(
        RuntimeDeadline.class);
    verify(runtime, times(2)).proposeBefore(any(), deadlines.capture(), any());
    assertThat(deadlines.getAllValues().get(0))
        .isSameAs(deadlines.getAllValues().get(1));
    verify(novelty).requireValidRewrite(repeated, accepted);
    verify(transactions).completeAsk(new AdaptiveAskIntentCompletion(
        "session-1", "intent-1", publication(rewritten)));
  }

  private ActionIntent planned() {
    return ActionIntent.planned(
        new ActionIntentKey("intent-1", "session-1", 1),
        new AskActionPayload(
            new ActionTarget("target-0", "issue-1", 2),
            "intent-1",
            new AskActionContext(NextTurnProvenanceDraft.planned(), null)
        ),
        now()
    );
  }

  private QuestionReview accepted(RespondAction question) {
    return new QuestionReview(
        QuestionNoveltyDecision.Type.ACCEPT, publication(question), List.of());
  }

  private QuestionPublication publication(RespondAction question) {
    return new QuestionPublication(question, new QuestionIdentity(
        new TopicKey("java-backend", "REDIS"),
        "验证持久化机制",
        DepthLevel.L2,
        "L2",
        "scenario",
        "wording"
    ), null, null);
  }

  private LocalDateTime now() {
    return LocalDateTime.of(2026, 8, 28, 8, 0);
  }
}
