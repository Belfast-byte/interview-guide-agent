package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.*;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceValidator;
import interview.guide.modules.interview.agent.adaptive.core.context.*;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.*;
import interview.guide.modules.interview.agent.adaptive.planning.*;
import interview.guide.modules.interview.skill.InterviewSkillService;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CodeRepairAssessmentContextTest {
  @Test
  void onlyPracticeReceivesPreviouslyPublishedCodeFeedback() {
    for (var mode : SessionMode.values()) {
      var request = captureRequest(mode, false);
      assertThat(request.context().priorTurns()).singleElement()
          .satisfies(turn -> assertThat(turn.submittedCode()).isEqualTo("first submission"));
      assertThat(request.context().codeTaskContext().submittedCode()).isEqualTo("revised submission");
      var feedback = request.context().codeTaskContext().priorReviews();
      if (mode == SessionMode.PRACTICE) {
        assertThat(feedback).singleElement().satisfies(review -> {
          assertThat(review.turnIndex()).isEqualTo(1);
          assertThat(review.rationale()).isEqualTo("已公开原子性提示");
          assertThat(review.codeReview().checks().getFirst().result()).isEqualTo(CodeRepairReview.Result.NOT_SATISFIED);
        });
      } else {
        assertThat(feedback).isEmpty();
      }
    }
  }

  @Test
  void codeRevisionReceivesPublishedTextFollowUpFeedbackButEvaluationDoesNot() {
    var practice = captureRequest(SessionMode.PRACTICE, true).context();
    assertThat(practice.priorTurns()).extracting(AdaptiveInterviewTurn.AnswerContext::turnIndex)
        .containsExactly(1, 2);
    assertThat(practice.codeTaskContext().priorReviews()).hasSize(2);
    var textFeedback = practice.codeTaskContext().priorReviews().get(1);
    assertThat(textFeedback.turnIndex()).isEqualTo(2);
    assertThat(textFeedback.codeReview()).isNull();
    assertThat(textFeedback.rationale()).isEqualTo("已公开提示：需要处理原子扣减失败");
    var evaluation = captureRequest(SessionMode.EVALUATION, true).context();
    assertThat(evaluation.codeTaskContext().priorReviews()).isEmpty();
    assertThat(evaluation.priorTurns()).allSatisfy(turn -> assertThat(turn.codeReview()).isNull());
  }

  private AdaptiveInterviewTurn textHint() {
    return new AdaptiveInterviewTurn(2, 0, "扣减失败应该如何反馈", "追问边界", "返回成功", null, null, null,
        TurnProvenance.agentDecision(1), List.of(), AnswerProcessingStatus.COMPLETED, null,
        CodeRepairTask.QuestionType.TEXT, null, 1, null,
        new AdaptiveInterviewTurn.AssessmentFeedback(DepthLevel.L1,
            "已公开提示：需要处理原子扣减失败", null, List.of()));
  }

  private AssessmentRequest captureRequest(SessionMode mode, boolean withTextHint) {
    var settings = new InterviewSessionSettings(mode, CandidateLevel.CAMPUS,
        mode == SessionMode.PRACTICE ? new PracticeScope(List.of(new TopicKey("java-backend", "JAVA")))
            : PracticeScope.none());
    var plan = InterviewPlan.decide("s", new PlanProposal(List.of(
        new DimensionProposal("Java", "锁机制", "JAVA", 2, "java-backend"))), settings);
    var prior = turn(1, "first submission", task()).withAssessmentFeedback(
        new AdaptiveInterviewTurn.AssessmentFeedback(DepthLevel.L1, "已公开原子性提示",
            review(CodeRepairReview.Result.NOT_SATISFIED), List.of()));
    int currentIndex = withTextHint ? 3 : 2;
    var current = turn(currentIndex, null, null);
    var history = new AdaptiveInterviewHistory(AdaptiveInterviewSession.create("s", plan.maxTurns(), settings),
        "candidate", "JD不能进入评分", "简历不能进入评分", "provider", withTextHint ? List.of(prior, textHint(), current) : List.of(prior, current));
    var captured = new AtomicReference<AssessmentRequest>();
    var service = new AdaptiveAnswerAssessmentService(new DepthAssessmentAgent((request, provider) -> {
      captured.set(request);
      return new AssessmentProposal(DepthLevel.L0, 0.8, "静态审阅", List.of(), List.of(), List.of(),
          review(CodeRepairReview.Result.SATISFIED));
    }), new AssessmentEvidenceValidator(), mock(InterviewSkillService.class));
    service.assess(new PlannedInterview(history, plan),
        new CandidateAnswer(currentIndex, null, null, new CandidateAnswer.CodeRepairAnswer("revised submission")));
    return captured.get();
  }

  private AdaptiveInterviewTurn turn(int index, String code, CodeRepairTask task) {
    return new AdaptiveInterviewTurn(index, 0, "修复并发", "考察机制", null, null, null, null,
        index == 1 ? TurnProvenance.initial() : TurnProvenance.agentDecision(1), List.of(),
        code == null ? AnswerProcessingStatus.WAITING : AnswerProcessingStatus.COMPLETED,
        null, CodeRepairTask.QuestionType.CODE_REPAIR, task, 1, code, null);
  }

  private CodeRepairReview review(CodeRepairReview.Result result) {
    return new CodeRepairReview(List.of(new CodeRepairReview.CheckReview("C1", result, "原子性要求")));
  }

  private CodeRepairTask task() {
    return new CodeRepairTask("initial", List.of("原子性"), List.of("多实例"),
        new CodeRepairTask.ReviewGuide(List.of(new CodeRepairTask.Check("C1", "竞态", "并发", "原子更新"))));
  }
}
