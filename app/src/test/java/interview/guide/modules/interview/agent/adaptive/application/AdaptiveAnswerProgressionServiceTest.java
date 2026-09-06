package interview.guide.modules.interview.agent.adaptive.application;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAnswerClaimService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAnswerClaimService.ClaimResult;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAnswerTransactionService;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AdaptiveAnswerProgressionServiceTest {

  @ParameterizedTest
  @EnumSource(value = ClaimResult.class, names = {"PENDING", "COMMITTED"})
  @DisplayName("处理中或已完成回答重放不会再次执行评估与生成")
  void shouldNotRepeatExpensiveExecution(ClaimResult claimed) {
    var claims = mock(AdaptiveAnswerClaimService.class);
    var decisions = mock(AdaptiveAnswerDecisionService.class);
    var transactions = mock(AdaptiveAnswerTransactionService.class);
    var executor = mock(DeadlineExecutor.class);
    var interview = mock(PlannedInterview.class, RETURNS_DEEP_STUBS);
    when(interview.history().session().id()).thenReturn("session-1");
    var owner = new MemoryOwner(null, "candidate-1");
    var answer = new CandidateAnswer(1, "原答案");
    when(claims.claim(eq("session-1"), eq(owner), eq(answer), anyString(), any()))
        .thenReturn(claimed);

    new AdaptiveAnswerProgressionService(claims, decisions, transactions, executor).advance(
        new AdaptiveAnswerProgressionService.AnswerProgressionCommand(owner, interview,
            new AdaptiveAnswerProgressionService.Submission(answer, AnswerEventSink.noop(), Duration.ofSeconds(60))));

    verifyNoInteractions(decisions, transactions, executor);
    verify(claims, never()).fail(anyString(), any(), anyInt(), anyString(), anyString());
  }
}
