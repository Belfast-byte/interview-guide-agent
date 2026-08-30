package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntent;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentStatus;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionResultType;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionPayload;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStateOperation;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatchSource;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionNoveltyDecision;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionPublication;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionReview;
import interview.guide.modules.interview.agent.adaptive.persistence.intent.ActionIntentPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.intent.ActionIntentTransactionService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAskIntentCompletion;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.working.WorkStatePersistenceService;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.BoundedActionRuntime;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import interview.guide.modules.interview.agent.adaptive.runtime.RuntimeDeadline;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 执行已持久化的 ASK，并把问题发布与状态应用分成两个短事务。 */
@Service
@RequiredArgsConstructor
public class ActionIntentExecutor {

  private final BoundedActionRuntime runtime;
  private final AdaptiveAgentProperties properties;
  private final ActionIntentPersistenceService intentService;
  private final ActionIntentTransactionService intentTransactions;
  private final WorkStatePersistenceService workStateService;
  private final AdaptiveInterviewPersistenceService interviewPersistence;
  private final QuestionNoveltyService noveltyService;

  public PlannedInterview executeAsk(AskIntentExecution execution) {
    ActionIntent running = begin(execution.intent());
    if (running.progress().status() == ActionIntentStatus.APPLIED) {
      return interviewPersistence.get(running.key().sessionId());
    }
    if (running.progress().status() == ActionIntentStatus.SUCCEEDED) {
      return applyQuestion(running);
    }
    QuestionPublication question = prepareQuestion(execution, running);
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



  public PlannedInterview applySucceededAsk(ActionIntent intent) {
    return applyQuestion(intent);
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
    QuestionPublication question = prepareQuestion(execution, execution.intent());
    intentTransactions.completeAsk(new AdaptiveAskIntentCompletion(
        execution.intent().key().sessionId(),
        execution.intent().key().intentId(),
        question
    ));
    publishQuestion(execution, question);
    return applyQuestion(intentService.get(execution.intent().key().intentId()));
  }


  private QuestionPublication prepareQuestion(
      AskIntentExecution execution,
      ActionIntent running
  ) {
    try {
      RuntimeDeadline deadline = RuntimeDeadline.start(properties.getDeadline());
      RespondAction draft = proposeQuestion(execution.request(), deadline);
      QuestionReview first = noveltyService.review(execution.request(), draft);
      if (first.type() == QuestionNoveltyDecision.Type.ACCEPT) {
        return first.publication();
      }
      ReActRequest rewriteRequest = noveltyService.rewriteRequest(execution.request(), first);
      QuestionReview rewritten = noveltyService.review(
          rewriteRequest, proposeQuestion(rewriteRequest, deadline));
      noveltyService.requireValidRewrite(first, rewritten);
      return rewritten.publication();
    } catch (RuntimeException e) {
      intentService.fail(running.key().intentId(), failureMessage(e));
      throw e;
    }
  }

  private RespondAction proposeQuestion(
      ReActRequest request,
      RuntimeDeadline deadline
  ) {
    AgentAction proposal = runtime.proposeBefore(request, deadline, null);
    if (proposal instanceof RespondAction response
        && response.type() == AgentResponseType.ASK) {
      return response;
    }
    throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "ASK Intent 未生成单一问题");
  }

  private void publishQuestion(AskIntentExecution execution, QuestionPublication question) {
    if (execution.deltaSink() != null) {
      execution.deltaSink().accept(question.action().content());
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
