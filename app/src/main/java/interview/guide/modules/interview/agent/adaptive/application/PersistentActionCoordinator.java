package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.action.ToolCallAction;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerWorkView;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntent;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentStatus;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentType;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionTarget;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionContext;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionPayload;
import interview.guide.modules.interview.agent.adaptive.core.memory.NextActionType;
import interview.guide.modules.interview.agent.adaptive.core.session.NextTurnProvenanceDraft;
import interview.guide.modules.interview.agent.adaptive.persistence.intent.ActionIntentPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.intent.ActionIntentTransactionService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptivePlannedAction;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.role.AgentRoleRegistry;
import interview.guide.modules.interview.agent.adaptive.runtime.BoundedActionRuntime;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecution;
import interview.guide.modules.interview.agent.adaptive.tool.ToolGateway;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class PersistentActionCoordinator {

  private final AdaptiveInterviewPersistenceService persistenceService;
  private final ActionIntentPersistenceService intentService;
  private final ActionIntentTransactionService intentTransactions;
  private final ActionIntentExecutor intentExecutor;
  private final BoundedActionRuntime actionRuntime;
  private final AgentRoleRegistry roleRegistry;
  private final ToolGateway toolGateway;
  private final AdaptiveInterviewRequestFactory requestFactory;

  PlannedInterview executeInitialAsk(
      AdaptivePlannedAction action,
      ReActRequest request,
      Consumer<String> deltaSink
  ) {
    return intentExecutor.executeAsk(new AskIntentExecution(
        action.intent(), request, deltaSink));
  }

  PlannedInterview execute(PreparedActionExecution input) {
    AdaptivePlannedAction action = input.action();
    if (action.intent().payload().type() == ActionIntentType.ASK) {
      AskActionContext context = ((AskActionPayload) action.intent().payload()).context();
      return intentExecutor.executeAsk(new AskIntentExecution(
          action.intent(), questionRequest(input.request(), action.intent(), context),
          input.sink().deltaSink()));
    }
    intentExecutor.executeTool(new ToolIntentExecution(action.intent(), input.request()));
    ToolExecution execution = intentTransactions.toolExecution(
        action.intent().payload().idempotencyKey());
    return continueAnswer(new ContinuationInput(
        persistenceService.get(input.interview().history().session().id()),
        input.answer(), input.sink(), toolEvent(execution)));
  }

  PlannedInterview continueAnswer(ContinuationInput input) {
    var state = input.interview().workState();
    WorkStatePolicyPlanner.PolicyDecision policy = WorkStatePolicyPlanner.decide(
        state, "resume:" + state.revision());
    if (policy.action().type() == NextActionType.FINISH) {
      return persistenceService.completePreparedAnswer(
          input.interview().history().session().id(),
          RespondAction.finish("面试已覆盖全部能力目标。", "工作状态中的能力目标均已终态"),
          policy.patches());
    }
    ReActRequest request = requestFactory.action(
        input.interview(), input.answer(),
        InterviewerWorkView.from(policy.state(), policy.action().issueId()));
    AdaptivePlannedAction action = continuationAction(policy, input, request);
    intentTransactions.planAction(
        input.interview().history().session().id(), policy.patches(), action);
    input.sink().onStage(AnswerEventSink.AnswerStage.GENERATING);
    return execute(new PreparedActionExecution(
        action, request, input.interview(), input.answer(), input.sink()));
  }

  PlannedInterview resume(
      PlannedInterview interview,
      CandidateAnswer answer,
      AnswerEventSink sink
  ) {
    ActionIntent intent = intentService.get(interview.workState().activeActionIntentId());
    ReActRequest request = requestFactory.action(
        interview,
        answer,
        InterviewerWorkView.from(interview.workState(), intent.payload().target().issueId())
    );
    if (intent.payload().type() == ActionIntentType.ASK) {
      AskActionContext context = ((AskActionPayload) intent.payload()).context();
      return intentExecutor.executeAsk(new AskIntentExecution(
          intent, questionRequest(request, intent, context), sink.deltaSink()));
    }
    intentExecutor.executeTool(new ToolIntentExecution(intent, request));
    ToolExecution execution = intentTransactions.toolExecution(intent.payload().idempotencyKey());
    return continueAnswer(new ContinuationInput(
        persistenceService.get(interview.history().session().id()),
        answer, sink, toolEvent(execution)));
  }

  void recover(ActionIntent intent) {
    if (isSucceededAsk(intent)) {
      intentExecutor.applySucceededAsk(intent);
      return;
    }
    PlannedInterview interview = persistenceService.get(intent.key().sessionId());
    CandidateAnswer answer = intentTransactions
        .currentCandidateAnswer(intent.key().sessionId()).orElse(null);
    ReActRequest request = requestFactory.action(
        interview,
        answer,
        InterviewerWorkView.from(interview.workState(), intent.payload().target().issueId())
    );
    if (intent.payload().type() == ActionIntentType.ASK) {
      recoverAsk(intent, request);
      return;
    }
    recoverTool(intent, request, answer);
  }

  void retry(String sessionId, String failedIntentId) {
    ActionIntent failed = intentService.get(failedIntentId);
    if (!failed.key().sessionId().equals(sessionId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "ActionIntent 不属于当前会话");
    }
    recover(intentService.retry(failedIntentId));
  }

  ToolCallAction proposeTool(ReActRequest request) {
    AgentAction proposal = actionRuntime.propose(
        request, roleRegistry.get(request.role()).deadline(), null);
    if (!(proposal instanceof ToolCallAction toolCall)) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "CALL_TOOL 未生成工具参数提案");
    }
    toolGateway.validate(request, toolCall);
    return toolCall;
  }

  private AdaptivePlannedAction continuationAction(
      WorkStatePolicyPlanner.PolicyDecision policy,
      ContinuationInput input,
      ReActRequest request
  ) {
    ActionTarget target = new ActionTarget(
        policy.state().activeTargetId(),
        policy.action().issueId(),
        policy.action().type() == NextActionType.ASK
            ? input.answer().turnIndex() + 1
            : input.answer().turnIndex()
    );
    if (policy.action().type() == NextActionType.ASK) {
      return ActionIntentPlanFactory.ask(
          policy.state(), target,
          new AskActionContext(NextTurnProvenanceDraft.planned(), input.toolResult()));
    }
    requireAction(policy.action().type(), NextActionType.CALL_TOOL);
    return ActionIntentPlanFactory.tool(policy.state(), target, proposeTool(request));
  }

  private ReActRequest questionRequest(
      ReActRequest fallback,
      ActionIntent intent,
      AskActionContext context
  ) {
    if (context.toolResult() == null) {
      return fallback;
    }
    return requestFactory.toolResult(
        persistenceService.get(fallback.sessionId()),
        context.toolResult(),
        intent.payload().target().issueId()
    );
  }

  private void recoverAsk(ActionIntent intent, ReActRequest fallback) {
    AskActionContext context = ((AskActionPayload) intent.payload()).context();
    AskIntentExecution execution = new AskIntentExecution(
        intent, questionRequest(fallback, intent, context), null);
    if (intent.progress().status() == ActionIntentStatus.EXECUTING) {
      intentExecutor.recoverAsk(execution);
      return;
    }
    intentExecutor.executeAsk(execution);
  }

  private void recoverTool(
      ActionIntent intent,
      ReActRequest request,
      CandidateAnswer answer
  ) {
    ToolIntentExecution execution = new ToolIntentExecution(intent, request);
    if (intent.progress().status() == ActionIntentStatus.SUCCEEDED) {
      intentExecutor.applySucceededTool(intent);
    } else if (intent.progress().status() == ActionIntentStatus.EXECUTING) {
      intentExecutor.recoverTool(execution);
    } else {
      intentExecutor.executeTool(execution);
    }
    ToolExecution result = intentTransactions.toolExecution(intent.payload().idempotencyKey());
    continueAnswer(new ContinuationInput(
        persistenceService.get(intent.key().sessionId()),
        answer,
        AnswerEventSink.noop(),
        toolEvent(result)
    ));
  }

  private boolean isSucceededAsk(ActionIntent intent) {
    return intent.progress().status() == ActionIntentStatus.SUCCEEDED
        && intent.payload().type() == ActionIntentType.ASK;
  }

  private ToolResultEvent toolEvent(ToolExecution execution) {
    return new ToolResultEvent(
        execution.turnIndex(), execution.toolName(), execution.resultId(),
        execution.outputSummary(), execution.output());
  }

  private void requireAction(NextActionType actual, NextActionType expected) {
    if (actual != expected) {
      throw new IllegalStateException("WorkState 策略动作与执行分支不一致");
    }
  }

  record PreparedActionExecution(
      AdaptivePlannedAction action,
      ReActRequest request,
      PlannedInterview interview,
      CandidateAnswer answer,
      AnswerEventSink sink
  ) {}

  record ContinuationInput(
      PlannedInterview interview,
      CandidateAnswer answer,
      AnswerEventSink sink,
      ToolResultEvent toolResult
  ) {}
}
