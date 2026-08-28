package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.action.ToolCallAction;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntent;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentStatus;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionResultType;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionPayload;
import interview.guide.modules.interview.agent.adaptive.core.intent.ToolActionPayload;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkEvidenceRef;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkIssueStatus;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStateOperation;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatchSource;
import interview.guide.modules.interview.agent.adaptive.persistence.intent.ActionIntentPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.intent.ActionIntentTransactionService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAskIntentCompletion;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.working.WorkStatePersistenceService;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.role.AgentRoleRegistry;
import interview.guide.modules.interview.agent.adaptive.runtime.BoundedActionRuntime;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecution;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecutionOutcome;
import interview.guide.modules.interview.agent.adaptive.tool.ToolGateway;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 执行已持久化的 ASK/CALL_TOOL，并把结果与状态应用分成两个短事务。 */
@Service
@RequiredArgsConstructor
public class ActionIntentExecutor {

  private final BoundedActionRuntime runtime;
  private final AgentRoleRegistry roleRegistry;
  private final ToolGateway toolGateway;
  private final ActionIntentPersistenceService intentService;
  private final ActionIntentTransactionService intentTransactions;
  private final WorkStatePersistenceService workStateService;
  private final AdaptiveInterviewPersistenceService interviewPersistence;

  public PlannedInterview executeAsk(AskIntentExecution execution) {
    ActionIntent running = begin(execution.intent());
    if (running.progress().status() == ActionIntentStatus.APPLIED) {
      return interviewPersistence.get(running.key().sessionId());
    }
    if (running.progress().status() == ActionIntentStatus.SUCCEEDED) {
      return applyQuestion(running);
    }
    RespondAction question = proposeQuestion(execution, running);
    intentTransactions.completeAsk(new AdaptiveAskIntentCompletion(
        running.key().sessionId(), running.key().intentId(), question));
    publishQuestion(execution, question);
    return applyQuestion(intentService.get(running.key().intentId()));
  }

  public PlannedInterview recoverAsk(AskIntentExecution execution) {
    ActionIntent restarted = intentService.restart(execution.intent().key().intentId());
    return executeRunningAsk(new AskIntentExecution(
        restarted, execution.request(), execution.deltaSink()));
  }

  public InterviewWorkState executeTool(ToolIntentExecution execution) {
    ActionIntent running = begin(execution.intent());
    if (running.progress().status() == ActionIntentStatus.APPLIED) {
      return workStateService.get(running.key().sessionId());
    }
    if (running.progress().status() == ActionIntentStatus.SUCCEEDED) {
      return applyTool(running);
    }
    ToolExecution result = invokeTool(execution.request(), running);
    intentTransactions.completeTool(
        running.key().sessionId(), running.key().intentId(), result);
    return applyTool(intentService.get(running.key().intentId()));
  }

  public InterviewWorkState recoverTool(ToolIntentExecution execution) {
    ActionIntent restarted = intentService.restart(execution.intent().key().intentId());
    return executeRunningTool(new ToolIntentExecution(restarted, execution.request()));
  }

  public PlannedInterview applySucceededAsk(ActionIntent intent) {
    return applyQuestion(intent);
  }

  public InterviewWorkState applySucceededTool(ActionIntent intent) {
    return applyTool(intent);
  }

  private ActionIntent begin(ActionIntent intent) {
    return switch (intent.progress().status()) {
      case PLANNED -> intentService.start(intent.key().intentId());
      case EXECUTING -> throw new BusinessException(
          ErrorCode.BAD_REQUEST,
          "ActionIntent 正在执行，只有超时恢复任务可以重新执行"
      );
      case SUCCEEDED -> intent;
      case APPLIED -> intent;
      case FAILED -> throw new BusinessException(
          ErrorCode.BAD_REQUEST,
          "ActionIntent 已失败，只能显式创建新 Intent 重试"
      );
    };
  }

  private PlannedInterview executeRunningAsk(AskIntentExecution execution) {
    RespondAction question = proposeQuestion(execution, execution.intent());
    intentTransactions.completeAsk(new AdaptiveAskIntentCompletion(
        execution.intent().key().sessionId(),
        execution.intent().key().intentId(),
        question
    ));
    publishQuestion(execution, question);
    return applyQuestion(intentService.get(execution.intent().key().intentId()));
  }

  private InterviewWorkState executeRunningTool(ToolIntentExecution execution) {
    ToolExecution result = invokeTool(execution.request(), execution.intent());
    intentTransactions.completeTool(
        execution.intent().key().sessionId(),
        execution.intent().key().intentId(),
        result
    );
    return applyTool(intentService.get(execution.intent().key().intentId()));
  }

  private RespondAction proposeQuestion(
      AskIntentExecution execution,
      ActionIntent running
  ) {
    AgentAction proposal;
    try {
      proposal = runtime.propose(
          execution.request(),
          roleRegistry.get(execution.request().role()).deadline(),
          null
      );
    } catch (RuntimeException e) {
      intentService.fail(running.key().intentId(), failureMessage(e));
      throw e;
    }
    if (proposal instanceof RespondAction response
        && response.type() == AgentResponseType.ASK) {
      return response;
    }
    intentService.fail(running.key().intentId(), "ASK Intent 未生成单一问题");
    throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "ASK Intent 未生成单一问题");
  }

  private void publishQuestion(AskIntentExecution execution, RespondAction question) {
    if (execution.deltaSink() != null) {
      execution.deltaSink().accept(question.content());
    }
  }

  private ToolExecution invokeTool(
      ReActRequest request,
      ActionIntent running
  ) {
    ToolActionPayload payload = (ToolActionPayload) running.payload();
    ToolCallAction action = new ToolCallAction(
        payload.call().toolName(), payload.call().arguments(), payload.call().reason());
    try {
      return toolGateway.execute(request, action, payload.idempotencyKey());
    } catch (RuntimeException e) {
      intentService.fail(running.key().intentId(), failureMessage(e));
      throw e;
    }
  }

  private PlannedInterview applyQuestion(ActionIntent intent) {
    if (intent.progress().status() == ActionIntentStatus.APPLIED) {
      return interviewPersistence.get(intent.key().sessionId());
    }
    AskActionPayload payload = (AskActionPayload) intent.payload();
    InterviewWorkState state = workStateService.get(intent.key().sessionId());
    intentService.apply(intent.key().intentId(), resultPatch(
        state,
        intent,
        List.of(new WorkStateOperation.ApplyActionResult(
            ActionResultType.QUESTION,
            payload.target().turnIndex(),
            payload.target().issueId()
        ))
    ));
    return interviewPersistence.get(intent.key().sessionId());
  }

  private InterviewWorkState applyTool(ActionIntent intent) {
    if (intent.progress().status() == ActionIntentStatus.APPLIED) {
      return workStateService.get(intent.key().sessionId());
    }
    ToolActionPayload payload = (ToolActionPayload) intent.payload();
    ToolExecution execution = intentTransactions.toolExecution(
        intent.progress().outcome().resultRef());
    InterviewWorkState state = workStateService.get(intent.key().sessionId());
    List<WorkStateOperation> operations = toolResultOperations(state, payload, execution);
    intentService.apply(intent.key().intentId(), resultPatch(state, intent, operations));
    return workStateService.get(intent.key().sessionId());
  }

  private List<WorkStateOperation> toolResultOperations(
      InterviewWorkState state,
      ToolActionPayload payload,
      ToolExecution execution
  ) {
    List<WorkStateOperation> operations = new ArrayList<>();
    if (execution.outcome() == ToolExecutionOutcome.COMPLETED) {
      operations.add(new WorkStateOperation.AddEvidenceRef(new WorkEvidenceRef(
          state.activeTargetId(), execution.toolName(), execution.resultId(),
          execution.outputSummary())));
      closeToolIssue(state, payload).ifPresent(operations::add);
    }
    operations.add(new WorkStateOperation.ApplyActionResult(
        ActionResultType.TOOL_RESULT, null, null));
    return List.copyOf(operations);
  }

  private java.util.Optional<WorkStateOperation> closeToolIssue(
      InterviewWorkState state,
      ToolActionPayload payload
  ) {
    if (payload.target().issueId() == null) {
      return java.util.Optional.empty();
    }
    return state.activeOpenIssues().stream()
        .filter(issue -> issue.issueId().equals(payload.target().issueId()))
        .findFirst()
        .map(issue -> new WorkStateOperation.CloseIssue(
            issue.issueId(), WorkIssueStatus.RESOLVED, "工具事实已经返回"));
  }

  private WorkStatePatch resultPatch(
      InterviewWorkState state,
      ActionIntent intent,
      List<WorkStateOperation> operations
  ) {
    return new WorkStatePatch(
        UUID.randomUUID().toString(),
        state.sessionId(),
        state.revision(),
        state.revision() + 1,
        WorkStatePatchSource.ACTION_RESULT,
        "intent:" + intent.key().intentId(),
        operations
    );
  }

  private String failureMessage(RuntimeException error) {
    return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
  }
}
