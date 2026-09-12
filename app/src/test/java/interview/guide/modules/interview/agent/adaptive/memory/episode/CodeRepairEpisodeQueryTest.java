package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeRepairReview;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType;
import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveTurnCreation;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest(showSql = false, properties = {
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(EpisodeQueryService.class)
class CodeRepairEpisodeQueryTest {
  private static final MemoryOwner OWNER = new MemoryOwner(null, "candidate");
  private static final TopicKey TOPIC = new TopicKey("java-backend", "CONCURRENCY");
  private static final String SESSION = "code-episode";
  private static final String PRIVATE_GUIDE = "PRIVATE_GUIDE_ONLY";
  private static final String REVIEW_REASON = "条件扣减保护共享库存";

  @Autowired private AdaptiveAgentSessionRepository sessions;
  @Autowired private AdaptiveAgentTurnRepository turns;
  @Autowired private AdaptiveAgentAssessmentRepository assessments;
  @Autowired private EpisodeFactRepository episodes;
  @Autowired private EpisodeQueryService query;
  @Autowired private AssessmentProbeGapRepository gaps;

  @ParameterizedTest
  @CsvSource({"PRACTICE,IN_PROGRESS,true", "EVALUATION,IN_PROGRESS,false", "EVALUATION,COMPLETED,true"})
  void projectsTaskAndPublishesReviewAtTheCorrectTime(SessionMode mode, AdaptiveSessionStatus status, boolean visible) {
    createSession(mode, status);
    saveOriginal(mode);
    var view = query.latest(OWNER).getFirst();
    assertThat(view.questionType()).isEqualTo(QuestionType.CODE_REPAIR);
    assertThat(view.codeTaskTurnIndex()).isEqualTo(1);
    assertThat(view.codeTask().initialCode()).isEqualTo("void reserve() {}\n");
    assertThat(view.submittedCode()).isEqualTo("void reserve() { stocks.tryReserve(); }\n");
    if (visible) {
      assertThat(view.codeReview().checks().getFirst().reason()).isEqualTo(REVIEW_REASON);
      assertThat(view.rationaleSummary()).isEqualTo(REVIEW_REASON);
    } else {
      assertThat(view.codeReview()).isNull();
      assertThat(view.rationaleSummary()).isNull();
    }
    String json = JsonMapper.builder().build().writeValueAsString(view);
    assertThat(json).doesNotContain(PRIVATE_GUIDE, "reviewGuide");
    if (!visible) assertThat(json).doesNotContain(REVIEW_REASON);
  }

  @ParameterizedTest
  @EnumSource(SessionMode.class)
  void priorReviewOnlyMeansFeedbackAvailableBeforeThisAnswer(SessionMode mode) {
    createSession(mode, AdaptiveSessionStatus.COMPLETED);
    saveOriginal(mode);
    var next = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(SESSION, 2, 0,
        RespondAction.ask("解释修复", "验证理解").withCodeTask(QuestionType.TEXT, null, 1),
        TurnProvenance.agentDecision(1)));
    next.recordAnswer(new CandidateAnswer(2, "数据库执行条件扣减"));
    turns.saveAndFlush(next);
    saveEpisode(next, mode, null);
    var view = query.latest(OWNER).getFirst();
    assertThat(view.codeTask().initialCode()).isEqualTo("void reserve() {}\n");
    assertThat(view.submittedCode()).isNull();
    assertThat(view.priorTurns()).singleElement().satisfies(prior -> {
      assertThat(prior.submittedCode()).contains("stocks.tryReserve()");
      if (mode == SessionMode.PRACTICE) assertThat(prior.codeReview()).isNotNull();
      else assertThat(prior.codeReview()).isNull();
    });
  }

  @Test
  void earlierTextEpisodeCannotRevealLaterCodeRepairClosureBeforeReport() {
    createSession(SessionMode.EVALUATION, AdaptiveSessionStatus.IN_PROGRESS);
    var text = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(SESSION, 1, 0,
        RespondAction.ask("如何预留库存", "考察并发"), TurnProvenance.initial()));
    text.recordAnswer(new CandidateAnswer(1, "先检查库存"));
    turns.saveAndFlush(text);
    var first = saveEpisode(text, SessionMode.EVALUATION, null);
    var gap = gaps.saveAndFlush(new AssessmentProbeGapEntity(first, 1,
        new ProbeGap(new SourceQuote(SourceQuote.Source.ANSWER_TEXT, "先检查库存", 0), "并发扣减机制")));
    var code = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(SESSION, 2, 0,
        RespondAction.ask("修复库存", "考察并发").withCodeTask(QuestionType.CODE_REPAIR, task(), null),
        TurnProvenance.agentDecision(1)));
    var answer = new CandidateAnswer(2, null, null, new CandidateAnswer.CodeRepairAnswer("stocks.tryReserve()"));
    code.recordAnswer(answer);
    turns.saveAndFlush(code);
    var closing = saveEpisode(code, SessionMode.EVALUATION, review());
    gap.closeByEvidence(closing, new SourceQuote(SourceQuote.Source.SUBMITTED_CODE, "stocks.tryReserve()", 0), REVIEW_REASON);
    gaps.flush();
    var prior = query.recent(OWNER, TOPIC, org.springframework.data.domain.PageRequest.of(0, 5)).getLast();
    assertThat(prior.gaps()).singleElement().satisfies(item -> {
      assertThat(item.closureEvidenceQuote()).isNull();
      assertThat(item.closureSummary()).isNull();
      assertThat(item.closureEvidenceLocator()).isNull();
    });
    var session = sessions.findById(SESSION).orElseThrow();
    session.apply(session.toDomain().apply(answer, RespondAction.finish("结束", "完成")));
    sessions.flush();
    var published = query.recent(OWNER, TOPIC, org.springframework.data.domain.PageRequest.of(0, 5)).getLast();
    assertThat(published.gaps()).singleElement().satisfies(item -> {
      assertThat(item.closureSummary()).isEqualTo(REVIEW_REASON);
      assertThat(item.closureEvidenceLocator().source()).isEqualTo(SourceQuote.Source.SUBMITTED_CODE);
    });
  }

  @ParameterizedTest
  @EnumSource(SessionMode.class)
  void revisionRetainsOnlyFeedbackPublishedBeforeCodeTextCodeAnswer(SessionMode mode) {
    createSession(mode, AdaptiveSessionStatus.COMPLETED);
    saveOriginal(mode);
    var followup = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(SESSION, 2, 0,
        RespondAction.ask("解释修复", "验证理解").withCodeTask(QuestionType.TEXT, null, 1),
        TurnProvenance.agentDecision(1)));
    followup.recordAnswer(new CandidateAnswer(2, "条件扣减保证库存约束"));
    turns.saveAndFlush(followup);
    saveEpisode(followup, mode, null);
    var revision = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(SESSION, 3, 0,
        RespondAction.ask("继续修复", "验证边界").withCodeTask(QuestionType.CODE_REPAIR, null, 1),
        TurnProvenance.agentDecision(2)));
    revision.recordAnswer(new CandidateAnswer(3, null, null,
        new CandidateAnswer.CodeRepairAnswer("void reserve() { stocks.tryReserve(); }\n")));
    turns.saveAndFlush(revision);
    saveEpisode(revision, mode, review());

    var prior = query.latest(OWNER).getFirst().priorTurns();
    assertThat(prior).extracting(AdaptiveInterviewTurn.AnswerContext::turnIndex).containsExactly(1, 2);
    assertThat(prior.getFirst().submittedCode()).contains("stocks.tryReserve()");
    assertThat(prior.getLast().submittedCode()).isNull();
    assertThat(prior.getLast().codeReview()).isNull();
    if (mode == SessionMode.PRACTICE) {
      assertThat(prior.getFirst().feedbackRationale()).isEqualTo(REVIEW_REASON);
      assertThat(prior.getLast().feedbackRationale()).isEqualTo("文字回答评估");
    } else {
      assertThat(prior).allSatisfy(turn -> {
        assertThat(turn.codeReview()).isNull();
        assertThat(turn.feedbackRationale()).isNull();
      });
    }
    var raw = revision.toDomain().withAssessmentFeedback(new AdaptiveInterviewTurn.AssessmentFeedback(
        DepthLevel.L2, REVIEW_REASON, review(), List.of())).answerContext();
    assertThat(raw.codeReview()).isNull();
    assertThat(raw.feedbackRationale()).isNull();
  }

  @Test
  void ordinaryTextPriorDoesNotPretendItsAssessmentWasPublishedAsCodeFeedback() {
    createSession(SessionMode.PRACTICE, AdaptiveSessionStatus.COMPLETED);
    var text = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(SESSION, 1, 0,
        RespondAction.ask("如何预留库存", "考察并发"), TurnProvenance.initial()));
    text.recordAnswer(new CandidateAnswer(1, "先检查库存"));
    turns.saveAndFlush(text);
    saveEpisode(text, SessionMode.PRACTICE, null);
    var code = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(SESSION, 2, 0,
        RespondAction.ask("修复库存", "考察并发").withCodeTask(QuestionType.CODE_REPAIR, task(), null),
        TurnProvenance.agentDecision(1)));
    code.recordAnswer(new CandidateAnswer(2, null, null, new CandidateAnswer.CodeRepairAnswer("stocks.tryReserve()")));
    turns.saveAndFlush(code);
    saveEpisode(code, SessionMode.PRACTICE, review());
    assertThat(query.latest(OWNER).getFirst().priorTurns()).singleElement().satisfies(prior -> {
      assertThat(prior.codeReview()).isNull();
      assertThat(prior.feedbackRationale()).isNull();
    });
  }

  private void createSession(SessionMode mode, AdaptiveSessionStatus status) {
    var scope = mode == SessionMode.PRACTICE ? new PracticeScope(List.of(TOPIC)) : PracticeScope.none();
    var settings = new InterviewSessionSettings(mode, CandidateLevel.EXPERIENCED, scope);
    var session = new AdaptiveInterviewSession(SESSION, AdaptiveInterviewSession.RUNTIME_VERSION, status, 2, 3, settings);
    var creation = new AdaptiveSessionCreation(null, SESSION, OWNER.candidateId(), "JD", "Resume",
        "provider", null, null, settings);
    sessions.saveAndFlush(new AdaptiveAgentSessionEntity(session, creation));
  }

  private void saveOriginal(SessionMode mode) {
    var turn = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(SESSION, 1, 0,
        RespondAction.ask("修复库存", "验证并发").withCodeTask(QuestionType.CODE_REPAIR, task(), null),
        TurnProvenance.initial()));
    turn.recordAnswer(new CandidateAnswer(1, null, null,
        new CandidateAnswer.CodeRepairAnswer("void reserve() { stocks.tryReserve(); }\n")));
    turns.saveAndFlush(turn);
    saveEpisode(turn, mode, review());
  }

  private AdaptiveAgentAssessmentEntity saveEpisode(AdaptiveAgentTurnEntity turn, SessionMode mode, CodeRepairReview review) {
    var decision = new AssessmentDecision(SESSION, turn.turnIndex(), DepthLevel.L2, 0.9,
        review == null ? "文字回答评估" : REVIEW_REASON, List.of(), List.of(), List.of(), review);
    var assessment = assessments.saveAndFlush(new AdaptiveAgentAssessmentEntity(0, decision));
    episodes.saveAndFlush(new EpisodeFactEntity(new EpisodeFactEntity.Creation(OWNER, SESSION,
        mode, turn.id(), turn.turnIndex(), TOPIC, "target-0"), assessment));
    return assessment;
  }
  private CodeRepairTask task() {
    return new CodeRepairTask("void reserve() {}\n", List.of("库存不能超卖"), List.of("共享数据库"),
        new CodeRepairTask.ReviewGuide(List.of(new CodeRepairTask.Check("C1", PRIVATE_GUIDE, "并发", "原子扣减"))));
  }

  private CodeRepairReview review() {
    return new CodeRepairReview(List.of(new CodeRepairReview.CheckReview(
        "C1", CodeRepairReview.Result.SATISFIED, REVIEW_REASON)));
  }

}
