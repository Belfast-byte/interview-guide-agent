package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAnswerAssessmentService.AnswerAssessment;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAnswerDecisionService.AnswerProgressionDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.*;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.*;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.*;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactPersistence;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactRepository;
import interview.guide.modules.interview.agent.adaptive.memory.episode.exposure.QuestionExposurePersistence;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.planning.*;
import interview.guide.modules.interview.agent.adaptive.rubric.RubricGenerationStore;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({tools.jackson.databind.ObjectMapper.class, RubricGenerationStore.class, RubricSnapshotResolver.class,
    AdaptiveCreationTransactionService.class, AdaptiveAssessmentRepositories.class,
    AdaptiveAnswerTransactionService.class, AdaptiveAnswerClaimService.class,
    QuestionExposurePersistence.class, AdaptiveAnswerSideEffects.class, EpisodeFactPersistence.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CodeRepairTransactionTest {
  private static final MemoryOwner OWNER = new MemoryOwner(null, "code-candidate");
  private static final Duration LEASE = Duration.ofMinutes(1);
  private static final Duration EXPIRED_LEASE = Duration.ofSeconds(-1);
  private static final int CONCURRENT_REQUESTS = 2;
  private static final int WAIT_SECONDS = 10;
  private static final String TOKEN = "active";
  private static final String CODE = "if (!reserve()) throw new SoldOut();";

  @Autowired AdaptiveCreationTransactionService creation;
  @Autowired AdaptiveAnswerClaimService claims;
  @Autowired AdaptiveAnswerTransactionService transactions;
  @Autowired AdaptiveAgentSessionRepository sessions;
  @Autowired AdaptiveAgentTurnRepository turns;
  @Autowired AdaptiveAgentAssessmentRepository assessments;
  @Autowired EpisodeFactRepository episodes;

  @Test
  void codeOnlyAnswerSurvivesModelFailureAndFencesOldExecution() {
    var plan = initialize(SessionMode.PRACTICE);
    var answer = answer(1, CODE);
    claim(plan, answer, "old");
    claims.fail(plan.sessionId(), OWNER, 1, "old", "评估模型失败");
    assertThat(turn(plan, 1).candidateAnswer()).isEqualTo(answer);
    assertThat(turn(plan, 1).toDomain().answerStatus()).isEqualTo(AnswerProcessingStatus.RETRYABLE);
    claim(plan, answer, TOKEN);
    claims.fail(plan.sessionId(), OWNER, 1, "old", "迟到失败");
    var commit = commit(plan, answer, finish());
    assertThatThrownBy(() -> transactions.commit(new AdaptiveAnswerTransactionService.AnswerCommit(
        OWNER, commit.interview(), commit.facts(), "old")))
        .isInstanceOf(BusinessException.class).hasMessageContaining("接管");
    assertThat(assessments.findBySessionIdAndTurnIndex(plan.sessionId(), 1)).isEmpty();
    assertThat(turn(plan, 1).toDomain().answerError()).isNull();
    transactions.commit(commit);
    assertThat(turn(plan, 1).candidateAnswer()).isEqualTo(answer);
    assertThat(turn(plan, 1).answer()).isNull();
  }

  @Test
  void expiredLeaseRecoversExactCodeIncludingWhitespace() {
    var plan = initialize(SessionMode.PRACTICE);
    var answer = answer(1, "// 😀\n  " + CODE + "\n");
    claims.claim(plan.sessionId(), OWNER, answer, "expired", EXPIRED_LEASE);
    assertThat(turn(plan, 1).candidateAnswer()).isEqualTo(answer);
    assertThat(claim(plan, answer, TOKEN)).isEqualTo(AdaptiveAnswerClaimService.ClaimResult.NEW);
    transactions.commit(commit(plan, answer, finish()));
    assertThat(turn(plan, 1).codeRepair().submittedCode()).isEqualTo(answer.codeRepair().code());
  }

  @Test
  void simultaneousClaimsAndCommitsProduceOneFormalAssessment() throws Exception {
    var plan = initialize(SessionMode.PRACTICE);
    var answer = answer(1, CODE);
    var gate = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)) {
      var first = workers.submit(() -> { gate.await(); return claim(plan, answer, TOKEN); });
      var second = workers.submit(() -> { gate.await(); return claim(plan, answer, "contender"); });
      gate.countDown();
      var firstResult = first.get(WAIT_SECONDS, TimeUnit.SECONDS);
      var secondResult = second.get(WAIT_SECONDS, TimeUnit.SECONDS);
      assertThat(List.of(firstResult, secondResult))
          .containsExactlyInAnyOrder(AdaptiveAnswerClaimService.ClaimResult.NEW, AdaptiveAnswerClaimService.ClaimResult.PENDING);
      assertThatThrownBy(() -> claim(plan,
          new CandidateAnswer(1, "改变说明", null, answer.codeRepair()), "different"))
          .isInstanceOf(BusinessException.class).hasMessageContaining("不同回答");
      var proposed = commit(plan, answer, ask(QuestionType.CODE_REPAIR, 1));
      var winner = firstResult == AdaptiveAnswerClaimService.ClaimResult.NEW ? TOKEN : "contender";
      var commit = new AdaptiveAnswerTransactionService.AnswerCommit(
          OWNER, proposed.interview(), proposed.facts(), winner);
      var saved = workers.submit(() -> transactions.commit(commit));
      var duplicate = workers.submit(() -> transactions.commit(commit));
      saved.get(WAIT_SECONDS, TimeUnit.SECONDS);
      duplicate.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }
    assertThat(claim(plan, answer, TOKEN)).isEqualTo(AdaptiveAnswerClaimService.ClaimResult.COMMITTED);
    assertThat(assessments.findBySessionIdOrderByDimensionOrderAscTurnIndexAsc(plan.sessionId())).hasSize(1);
    assertThat(episodes.countBySessionId(plan.sessionId())).isEqualTo(1);
    assertThat(turns.findBySessionIdOrderByTurnIndex(plan.sessionId())).hasSize(2);
    assertThatThrownBy(() -> claim(plan, answer(1, CODE + " // changed"), "different"))
        .isInstanceOf(BusinessException.class).hasMessageContaining("不同回答");
  }

  @Test
  void practiceRevisionReferencesRootAndNeverOverwritesPreviousCode() {
    var plan = initialize(SessionMode.PRACTICE);
    var first = answer(1, CODE);
    claim(plan, first, TOKEN);
    transactions.commit(commit(plan, first, ask(QuestionType.CODE_REPAIR, 1)));
    var revision = turn(plan, 2).codeRepair();
    assertThat(revision.codeTaskTurnIndex()).isEqualTo(1);
    assertThat(revision.codeTask()).isNull();
    var second = answer(2, CODE + " // revised");
    claim(plan, second, TOKEN);
    transactions.commit(commit(plan, second, finish()));
    assertThat(turn(plan, 1).candidateAnswer()).isEqualTo(first);
    assertThat(turn(plan, 1).codeRepair().codeTask()).isEqualTo(task());
    assertThat(turn(plan, 2).candidateAnswer()).isEqualTo(second);
    assertThat(episodes.countBySessionId(plan.sessionId())).isEqualTo(2);
  }

  @Test
  void evaluationAllowsOnlyTextFollowUpForExistingTask() {
    var plan = initialize(SessionMode.EVALUATION);
    var answer = answer(1, CODE);
    claim(plan, answer, TOKEN);
    assertRejectedWithoutProgress(plan, answer, ask(QuestionType.CODE_REPAIR, 1));
    transactions.commit(commit(plan, answer, ask(QuestionType.TEXT, 1)));
    assertThat(turn(plan, 2).codeRepair().questionType()).isEqualTo(QuestionType.TEXT);
    assertThat(turn(plan, 2).codeRepair().codeTaskTurnIndex()).isEqualTo(1);
    assertThatThrownBy(() -> claim(plan, answer(2, CODE), TOKEN)).isInstanceOf(BusinessException.class);
    assertThat(claim(plan, new CandidateAnswer(2, "解释实际提交的原子性"), TOKEN))
        .isEqualTo(AdaptiveAnswerClaimService.ClaimResult.NEW);
    assertThat(turn(plan, 1).candidateAnswer()).isEqualTo(answer);
  }

  @Test
  void cannotReferenceAnotherSessionOrARevisionInsteadOfOriginalTask() {
    var plan = initialize(SessionMode.PRACTICE);
    var other = initialize(SessionMode.PRACTICE);
    turns.saveAndFlush(new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(other.sessionId(), 2, 0,
        RespondAction.ask("另场代码题", "新任务").withCodeTask(QuestionType.CODE_REPAIR, task(), null),
        TurnProvenance.agentDecision(1), WorkingMemory.empty())));
    var first = answer(1, CODE);
    claim(plan, first, TOKEN);
    assertRejectedWithoutProgress(plan, first, ask(QuestionType.CODE_REPAIR, 2));
    transactions.commit(commit(plan, first, ask(QuestionType.CODE_REPAIR, 1)));
    var second = answer(2, CODE + " // revised");
    claim(plan, second, TOKEN);
    assertRejectedWithoutProgress(plan, second, ask(QuestionType.CODE_REPAIR, 2));
    assertThat(turn(plan, 1).candidateAnswer()).isEqualTo(first);
    assertThat(turn(plan, 2).candidateAnswer()).isEqualTo(second);
  }

  private void assertRejectedWithoutProgress(InterviewPlan plan, CandidateAnswer answer, AgentDecision next) {
    assertThatThrownBy(() -> transactions.commit(commit(plan, answer, next)))
        .isInstanceOfAny(IllegalArgumentException.class, org.springframework.dao.InvalidDataAccessApiUsageException.class)
        .hasMessageContaining("任务");
    assertThat(assessments.findBySessionIdAndTurnIndex(plan.sessionId(), answer.turnIndex())).isEmpty();
    assertThat(turns.findBySessionIdOrderByTurnIndex(plan.sessionId())).hasSize(answer.turnIndex());
    assertThat(episodes.countBySessionId(plan.sessionId())).isEqualTo(answer.turnIndex() - 1);
    assertThat(turn(plan, answer.turnIndex()).candidateAnswer()).isEqualTo(answer);
  }

  private InterviewPlan initialize(SessionMode mode) {
    var scope = mode == SessionMode.PRACTICE
        ? new PracticeScope(List.of(new TopicKey("java-backend", "JAVA"))) : PracticeScope.none();
    var settings = new InterviewSessionSettings(mode, CandidateLevel.CAMPUS, scope);
    var id = UUID.randomUUID().toString();
    var plan = InterviewPlan.decide(id, new PlanProposal(List.of(
        new DimensionProposal("Java", "库存扣减", "JAVA", 4, "java-backend"))), settings);
    creation.create(new AdaptiveSessionCreation(null, id, OWNER.candidateId(), "JD", "简历", "provider",
        null, null, settings), plan, new AgentDecision(WorkingMemory.empty(), new AgentDecision.Ask("target-0", null,
        new AgentDecision.QuestionDraft("修复库存超卖", "考察原子性", List.of(), QuestionType.CODE_REPAIR, task(), null))));
    return plan;
  }

  private AdaptiveAnswerTransactionService.AnswerCommit commit(InterviewPlan plan, CandidateAnswer answer, AgentDecision next) {
    var history = new AdaptiveInterviewHistory(sessions.findById(plan.sessionId()).orElseThrow().toDomain(),
        OWNER.candidateId(), "JD", "简历", "provider", turns.findBySessionIdOrderByTurnIndex(plan.sessionId())
            .stream().map(AdaptiveAgentTurnEntity::toDomain).toList());
    var quote = new SourceQuote(SourceQuote.Source.SUBMITTED_CODE, answer.codeRepair().code(), 0);
    var review = new CodeRepairReview(List.of(new CodeRepairReview.CheckReview(
        "C1", CodeRepairReview.Result.SATISFIED, "按已给定条件原子扣减")));
    var decision = new AssessmentDecision(plan.sessionId(), answer.turnIndex(), DepthLevel.L2, 0.8, "静态代码审阅",
        List.of(quote), List.of(), List.of(), review);
    var assessed = new AnswerAssessment(plan.dimension(0), decision, List.of(
        new ValidatedAssessmentEvidence(EvidenceType.QUOTE, quote.quote(), null, quote.locator())));
    return new AdaptiveAnswerTransactionService.AnswerCommit(OWNER, new PlannedInterview(history, plan),
        new AdaptiveAnswerTransactionService.CommitFacts(answer, new AnswerProgressionDecision(assessed, next)), TOKEN);
  }

  private AdaptiveAnswerClaimService.ClaimResult claim(InterviewPlan plan, CandidateAnswer answer, String token) {
    return claims.claim(plan.sessionId(), OWNER, answer, token, LEASE);
  }

  private AdaptiveAgentTurnEntity turn(InterviewPlan plan, int index) {
    return turns.findBySessionIdAndTurnIndex(plan.sessionId(), index).orElseThrow();
  }

  private CandidateAnswer answer(int index, String code) {
    return new CandidateAnswer(index, null, null, new CandidateAnswer.CodeRepairAnswer(code));
  }

  private AgentDecision ask(QuestionType type, int root) {
    return new AgentDecision(WorkingMemory.empty(), new AgentDecision.Ask("target-0", null,
        new AgentDecision.QuestionDraft("继续说明或修订", "追问业务边界", List.of(), type, null, root)));
  }

  private AgentDecision finish() {
    return new AgentDecision(WorkingMemory.empty(), new AgentDecision.Finish("评估完成"));
  }

  private CodeRepairTask task() {
    return new CodeRepairTask("stock--;", List.of("不超卖"), List.of("多实例共享数据库"),
        new CodeRepairTask.ReviewGuide(List.of(new CodeRepairTask.Check("C1", "竞争", "并发", "原子扣减"))));
  }
}
