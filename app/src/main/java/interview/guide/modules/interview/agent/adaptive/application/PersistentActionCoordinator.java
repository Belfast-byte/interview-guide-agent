package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerWorkView;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntent;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentStatus;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionContext;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionPayload;
import interview.guide.modules.interview.agent.adaptive.persistence.intent.ActionIntentPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.intent.ActionIntentTransactionService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** T13 删除前仅保留遗留 ASK Intent 的恢复入口。 */
@Service
@RequiredArgsConstructor
class PersistentActionCoordinator {

  private final AdaptiveInterviewPersistenceService persistenceService;
  private final ActionIntentPersistenceService intentService;
  private final ActionIntentTransactionService intentTransactions;
  private final ActionIntentExecutor intentExecutor;
  private final AdaptiveInterviewRequestFactory requestFactory;

  void recover(ActionIntent intent) {
    if (intent.progress().status() == ActionIntentStatus.SUCCEEDED) {
      intentExecutor.applySucceededAsk(intent);
      return;
    }
    PlannedInterview interview = persistenceService.get(intent.key().sessionId());
    CandidateAnswer answer = intentTransactions
        .currentCandidateAnswer(intent.key().sessionId()).orElse(null);
    AskActionPayload payload = (AskActionPayload) intent.payload();
    ReActRequest request = requestFactory.action(
        interview,
        answer,
        InterviewerWorkView.from(interview.workState(), payload.target().issueId())
    );
    recoverAsk(intent, questionRequest(request, payload));
  }

  void retry(String sessionId, String failedIntentId) {
    ActionIntent failed = intentService.get(failedIntentId);
    if (!failed.key().sessionId().equals(sessionId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "ActionIntent 不属于当前会话");
    }
    recover(intentService.retry(failedIntentId));
  }

  private void recoverAsk(ActionIntent intent, ReActRequest request) {
    AskIntentExecution execution = new AskIntentExecution(intent, request, null);
    if (intent.progress().status() == ActionIntentStatus.EXECUTING) {
      intentExecutor.recoverAsk(execution);
      return;
    }
    intentExecutor.executeAsk(execution);
  }

  private ReActRequest questionRequest(ReActRequest fallback, AskActionPayload payload) {
    if (payload.context().toolResult() == null) {
      return fallback;
    }
    return requestFactory.toolResult(
        persistenceService.get(fallback.sessionId()),
        payload.context().toolResult(),
        payload.target().issueId()
    );
  }
}
