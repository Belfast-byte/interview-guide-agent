package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAnswerAssessmentService.AnswerAssessment;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAnswerDecisionService.AnswerProgressionDecision;
import interview.guide.modules.interview.agent.adaptive.application.PendingAssessmentReferences;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AdaptiveAnswerProgressionTest extends AdaptiveAnswerPersistenceFixture {

  @Test
  @DisplayName("相同回答可重放且最终事实与下一 Turn 只提交一次")
  void shouldCommitAnswerProgressionOnce() {
    InterviewPlan plan = initializeInterview();
    MemoryOwner owner = new MemoryOwner(null, "candidate-1");
    CandidateAnswer answer = new CandidateAnswer(1, "我会使用版本号处理并发更新。");
    assertThat(claims.claim(SESSION_ID, owner, answer, "execution-1", java.time.Duration.ofMinutes(1)))
        .isEqualTo(AdaptiveAnswerClaimService.ClaimResult.NEW);
    assertThat(claims.claim(SESSION_ID, owner, answer, "execution-1", java.time.Duration.ofMinutes(1)))
        .isEqualTo(AdaptiveAnswerClaimService.ClaimResult.PENDING);

    PlannedInterview interview = interview(plan);
    var commit = new AdaptiveAnswerTransactionService.AnswerCommit(
        owner,
        interview,
        new AdaptiveAnswerTransactionService.CommitFacts(answer, progression(plan)), "execution-1"
    );
    transactions.commit(commit);
    transactions.commit(commit);
    entityManager.flush();
    entityManager.clear();

    assertThat(claims.claim(SESSION_ID, owner, answer, "execution-1", java.time.Duration.ofMinutes(1)))
        .isEqualTo(AdaptiveAnswerClaimService.ClaimResult.COMMITTED);
    assertThat(sessions.findById(SESSION_ID).orElseThrow().status())
        .isEqualTo(AdaptiveSessionStatus.IN_PROGRESS);
    assertThat(turns.findBySessionIdOrderByTurnIndex(SESSION_ID)).hasSize(2);
    assertThat(assessments.findBySessionIdOrderByDimensionOrderAscTurnIndexAsc(SESSION_ID))
        .hasSize(1);
    assertThat(gaps.findSessionGaps(SESSION_ID)).hasSize(1);
    assertThat(gaps.findSessionGaps(SESSION_ID).getFirst().closedByAssessmentId())
        .isNull();
    assertThat(evidences.findReportEvidence(SESSION_ID)).hasSize(1);
    var nextTurn = turns.findBySessionIdAndTurnIndex(SESSION_ID, 2).orElseThrow();
    assertThat(nextTurn.toDomain().provenance().trigger().sourceProbeGapId()).isPositive();
    assertThat(nextTurn.workingMemory().focus().activeGapId()).isPositive();
    assertThat(nextTurn.workingMemory().deliberation().hypotheses().getFirst()
        .evidenceLinks().supportingEvidenceIds().getFirst()).isPositive();
    verify(sideEffects, times(1)).saveEpisode(any(), any(), any());
    assertThat(episodes.countBySessionId(SESSION_ID)).isEqualTo(1);
    verify(sideEffects, times(1)).saveExposure(any());
  }

  @Test
  @DisplayName("不同回答重放明确冲突且不覆盖原 answer claim")
  void shouldRejectDifferentAnswerReplay() {
    initializeInterview();
    MemoryOwner owner = new MemoryOwner(null, "candidate-1");
    claims.claim(SESSION_ID, owner, new CandidateAnswer(1, "回答 A"), "execution-1", java.time.Duration.ofMinutes(1));

    assertThatThrownBy(() -> claims.claim(
        SESSION_ID, owner, new CandidateAnswer(1, "回答 B"), "execution-2", java.time.Duration.ofMinutes(1)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不同回答");
    assertThat(turns.findBySessionIdAndTurnIndex(SESSION_ID, 1).orElseThrow().answer())
        .isEqualTo("回答 A");
  }

  @Test
  @DisplayName("失败领取可重试，旧执行者不能提交或清除新租约")
  void shouldFenceFailedExecutionAfterRetry() {
    InterviewPlan plan = initializeInterview();
    MemoryOwner owner = new MemoryOwner(null, "candidate-1");
    CandidateAnswer answer = new CandidateAnswer(1, "我会使用版本号处理并发更新。");
    claims.claim(SESSION_ID, owner, answer, "old", java.time.Duration.ofMinutes(1));
    claims.fail(SESSION_ID, owner, 1, "old", "评估失败");
    assertThat(turns.findBySessionIdAndTurnIndex(SESSION_ID, 1).orElseThrow()
        .toDomain().answerStatus().name()).isEqualTo("RETRYABLE");
    assertThat(claims.claim(SESSION_ID, owner, answer, "new", java.time.Duration.ofMinutes(1)))
        .isEqualTo(AdaptiveAnswerClaimService.ClaimResult.NEW);
    claims.fail(SESSION_ID, owner, 1, "old", "迟到的失败");
    var turn = turns.findBySessionIdAndTurnIndex(SESSION_ID, 1).orElseThrow();
    turn.requireExecution("new");
    assertThat(turn.toDomain().answerError()).isNull();
    assertThatThrownBy(() -> transactions.commit(new AdaptiveAnswerTransactionService.AnswerCommit(
        owner, interview(plan), new AdaptiveAnswerTransactionService.CommitFacts(answer, progression(plan)), "old")))
        .isInstanceOf(BusinessException.class).hasMessageContaining("接管");
    assertThat(assessments.findBySessionIdAndTurnIndex(SESSION_ID, 1)).isEmpty();
  }

  @Test
  @DisplayName("租约到期后可重新领取，代码提交事实保持不变")
  void shouldRecoverExpiredCodeAnswerLease() {
    initializeInterview();
    MemoryOwner owner = new MemoryOwner(null, "candidate-1");
    CandidateAnswer answer = new CandidateAnswer(1, "class Solution {}",
        new interview.guide.modules.interview.agent.adaptive.core.event.CandidateCodeSubmission(
            "two-sum", null, "JAVA", "FULL"));
    claims.claim(SESSION_ID, owner, answer, "expired", java.time.Duration.ofSeconds(-1));
    assertThat(turns.findBySessionIdAndTurnIndex(SESSION_ID, 1).orElseThrow()
        .toDomain().answerStatus().name()).isEqualTo("RETRYABLE");
    assertThat(claims.claim(SESSION_ID, owner, answer, "replacement", java.time.Duration.ofMinutes(1)))
        .isEqualTo(AdaptiveAnswerClaimService.ClaimResult.NEW);
    assertThat(turns.findBySessionIdAndTurnIndex(SESSION_ID, 1).orElseThrow().candidateAnswer())
        .isEqualTo(answer);
  }

  private AnswerProgressionDecision progression(InterviewPlan plan) {
    AssessmentDecision assessment = new AssessmentDecision(
        SESSION_ID,
        1,
        DepthLevel.L2,
        0.8,
        "理解版本冲突",
        List.of(new SourceQuote(SourceQuote.Source.ANSWER_TEXT, "使用版本号", null)),
        List.of(new ProbeGap(new SourceQuote(SourceQuote.Source.ANSWER_TEXT, "版本号", 2), "缺少推进规则"))
    );
    AnswerAssessment assessed = new AnswerAssessment(
        plan.dimension(0),
        assessment,
        List.of(new ValidatedAssessmentEvidence(EvidenceType.QUOTE, "使用版本号", null,
            new SourceQuote.Locator(SourceQuote.Source.ANSWER_TEXT, 0, 5)))
    );
    WorkingMemory memory = new WorkingMemory(
        1,
        new WorkingMemory.Focus(
            "target-0",
            PendingAssessmentReferences.gapId(0),
            List.of()
        ),
        new WorkingMemory.Deliberation(
            List.of(new WorkingMemory.Hypothesis(
                "候选人理解乐观并发",
                "OPEN",
                new WorkingMemory.EvidenceLinks(
                    List.of(PendingAssessmentReferences.evidenceId(0)), List.of())
            )),
            "验证版本推进",
            List.of()
        )
    );
    AgentDecision decision = new AgentDecision(memory, new AgentDecision.Ask(
        "target-0",
        PendingAssessmentReferences.gapId(0),
        new AgentDecision.QuestionDraft("版本号如何推进？", "验证冲突细节", List.of(), QuestionType.TEXT, null, null)
    ));
    return new AnswerProgressionDecision(assessed, decision);
  }

}
