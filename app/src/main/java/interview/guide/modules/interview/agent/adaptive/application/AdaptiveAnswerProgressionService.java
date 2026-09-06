package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAnswerClaimService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAnswerClaimService.ClaimResult;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAnswerTransactionService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAnswerTransactionService.AnswerCommit;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAnswerTransactionService.CommitFacts;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import java.time.Duration;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

/** answer claim 后在事务外评估/决策，再以一个短事务提交最终事实。 */
@Service
public class AdaptiveAnswerProgressionService {

  private final AdaptiveAnswerClaimService claims;
  private final AdaptiveAnswerDecisionService decisions;
  private final AdaptiveAnswerTransactionService transactions;
  private final interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor deadlineExecutor;

  public AdaptiveAnswerProgressionService(
      AdaptiveAnswerClaimService claims,
      AdaptiveAnswerDecisionService decisions,
      AdaptiveAnswerTransactionService transactions,
      interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor deadlineExecutor
  ) {
    this.claims = claims;
    this.decisions = decisions;
    this.transactions = transactions;
    this.deadlineExecutor = deadlineExecutor;
  }

  public void advance(AnswerProgressionCommand command) {
    PlannedInterview interview = command.interview();
    CandidateAnswer answer = command.submission().answer();
    String sessionId = interview.history().session().id();
    long deadline = Math.min(command.submission().sink().deadlineNanos(),
        System.nanoTime() + command.submission().deadline().toNanos());
    long remaining = deadline - System.nanoTime();
    if (remaining <= 0) throw new BusinessException(
        ErrorCode.AI_SERVICE_TIMEOUT, "答题请求等待超时，请重试");
    String token = java.util.UUID.randomUUID().toString();
    ClaimResult claim = claims.claim(sessionId, command.owner(), answer, token, Duration.ofNanos(remaining));
    if (claim != ClaimResult.NEW) return;
    try {
      AnswerProgressionDecision decision = deadlineExecutor.invoke(() -> decisions.decide(
          new AdaptiveAnswerDecisionService.AnswerDecisionRequest(
              command.owner(), interview, answer, Duration.ofNanos(Math.max(0, deadline - System.nanoTime())),
              command.submission().sink())), deadline, "回答评估与决策");
      if (System.nanoTime() >= deadline) throw new BusinessException(
          ErrorCode.AI_SERVICE_TIMEOUT, "答题处理超时，请重试");
      transactions.commit(new AnswerCommit(command.owner(), interview, new CommitFacts(answer, decision), token));
    } catch (RuntimeException error) {
      claims.fail(sessionId, command.owner(), answer.turnIndex(), token, "回答处理失败，请重试原答案");
      throw error;
    }
  }

  public record AnswerProgressionCommand(
      MemoryOwner owner,
      PlannedInterview interview,
      Submission submission
  ) {}

  public record Submission(
      CandidateAnswer answer,
      AnswerEventSink sink,
      Duration deadline
  ) {}
}
