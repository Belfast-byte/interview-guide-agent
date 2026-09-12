package interview.guide.modules.interview.agent.adaptive.api;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;
import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeRepairReview;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn.AssessmentFeedback;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType;
import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveTurnCreation;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class CodeRepairResponseTest {
  private static final String PRIVATE_GUIDE = "PRIVATE_GUIDE_ONLY";
  private static final String REVIEW_REASON = "DETAILED_REPAIR_FEEDBACK";

  @ParameterizedTest
  @CsvSource({"PRACTICE,IN_PROGRESS,true", "EVALUATION,IN_PROGRESS,false", "EVALUATION,COMPLETED,true"})
  void projectsRootTaskAndFeedbackWithoutExposingPrivateGuide(SessionMode mode, AdaptiveSessionStatus status, boolean visible) {
    var response = AdaptiveInterviewResponse.from(interview(mode, status));
    var first = response.turns().getFirst();
    var followUp = response.turns().getLast();
    assertThat(first.submittedCode()).isEqualTo("stocks.tryReserve()");
    assertThat(followUp.questionType()).isEqualTo(QuestionType.TEXT);
    assertThat(followUp.submittedCode()).isNull();
    assertThat(followUp.codeTaskTurnIndex()).isEqualTo(1);
    assertThat(followUp.codeTask()).isEqualTo(first.codeTask());
    assertThat(first.codeReview() != null).isEqualTo(visible);
    assertThat(first.assessmentFeedback() != null).isEqualTo(visible);
    String json = JsonMapper.builder().build().writeValueAsString(response);
    assertThat(json).doesNotContain(PRIVATE_GUIDE, "reviewGuide", "内部决策");
    assertThat(json.contains(REVIEW_REASON)).isEqualTo(visible);
  }

  private PlannedInterview interview(SessionMode mode, AdaptiveSessionStatus status) {
    var scope = mode == SessionMode.PRACTICE
        ? new PracticeScope(List.of(new TopicKey("java-backend", "CONCURRENCY"))) : PracticeScope.none();
    var settings = new InterviewSessionSettings(mode, CandidateLevel.EXPERIENCED, scope);
    var session = new AdaptiveInterviewSession("session", AdaptiveInterviewSession.RUNTIME_VERSION, status, 2, 3, settings);
    var original = originalTurn();
    var followUp = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation("session", 2, 0,
        RespondAction.ask("说明条件扣减", "内部决策").withCodeTask(QuestionType.TEXT, null, 1),
        TurnProvenance.agentDecision(1)));
    var review = new CodeRepairReview(List.of(new CodeRepairReview.CheckReview(
        "C1", CodeRepairReview.Result.SATISFIED, REVIEW_REASON)));
    var feedback = new AssessmentFeedback(DepthLevel.L2, REVIEW_REASON, review,
        List.of(new SourceQuote(SourceQuote.Source.SUBMITTED_CODE, "stocks.tryReserve()", 0)));
    var history = new AdaptiveInterviewHistory(session, "candidate", "JD", "Resume", "provider",
        List.of(original.toDomain().withAssessmentFeedback(feedback), followUp.toDomain()));
    var plan = testPlan("session", new PlanProposal(List.of(
        new DimensionProposal("并发", "共享库存", "CONCURRENCY", 3, "java-backend"))));
    return new PlannedInterview(history, plan);
  }

  private AdaptiveAgentTurnEntity originalTurn() {
    var task = new CodeRepairTask("void reserve() {}", List.of("不能超卖"), List.of("共享数据库"),
        new CodeRepairTask.ReviewGuide(List.of(new CodeRepairTask.Check("C1", PRIVATE_GUIDE, "并发", "原子扣减"))));
    var turn = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation("session", 1, 0,
        RespondAction.ask("修复库存", "内部决策").withCodeTask(QuestionType.CODE_REPAIR, task, null),
        TurnProvenance.initial()));
    turn.recordAnswer(new CandidateAnswer(1, null, null, new CandidateAnswer.CodeRepairAnswer("stocks.tryReserve()")));
    turn.recordResponse(RespondAction.ask("说明条件扣减", "内部决策"));
    return turn;
  }
}
