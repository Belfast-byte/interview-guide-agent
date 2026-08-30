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
import org.springframework.stereotype.Service;

/** answer claim 后在事务外评估/决策，再以一个短事务提交最终事实。 */
@Service
public class AdaptiveAnswerProgressionService {

  private final AdaptiveAnswerClaimService claims;
  private final AdaptiveAnswerDecisionService decisions;
  private final AdaptiveAnswerTransactionService transactions;

  public AdaptiveAnswerProgressionService(
      AdaptiveAnswerClaimService claims,
      AdaptiveAnswerDecisionService decisions,
      AdaptiveAnswerTransactionService transactions
  ) {
    this.claims = claims;
    this.decisions = decisions;
    this.transactions = transactions;
  }

  public void advance(AnswerProgressionCommand command) {
    PlannedInterview interview = command.interview();
    CandidateAnswer answer = command.submission().answer();
    String sessionId = interview.history().session().id();
    ClaimResult claim = claims.claim(sessionId, command.owner(), answer);
    if (claim == ClaimResult.COMMITTED) {
      return;
    }
    command.submission().sink().onStage(AnswerEventSink.AnswerStage.ASSESSING);
    AnswerProgressionDecision decision = decisions.decide(
        new AdaptiveAnswerDecisionService.AnswerDecisionRequest(
            command.owner(), interview, answer, command.submission().deadline()));
    command.submission().sink().onStage(AnswerEventSink.AnswerStage.GENERATING);
    transactions.commit(new AnswerCommit(
        command.owner(), interview, new CommitFacts(answer, decision)));
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
