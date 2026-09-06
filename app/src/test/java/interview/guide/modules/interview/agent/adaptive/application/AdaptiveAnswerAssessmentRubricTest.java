package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.*;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceValidator;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.*;
import interview.guide.modules.interview.agent.adaptive.planning.*;
import interview.guide.modules.interview.skill.InterviewSkillService;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;

class AdaptiveAnswerAssessmentRubricTest {
  @Test void assessmentReceivesAdoptedBodyAndKeepsStandardWithoutOne() {
    for (boolean adopted : List.of(true,false)) {
      var plan=InterviewPlan.decide("s",new PlanProposal(List.of(
          new DimensionProposal("Java","并发","JAVA",2,"java-backend"))),EVALUATION_SETTINGS);
      var rubric=new AdoptedRubricSource("rubric:question:1:rubric@version","question:1:rubric","version","volatile 不保证 i++ 原子性");
      var turn=new AdaptiveInterviewTurn(1,plan.dimensions().getFirst().order(),"volatile 能保证 i++ 原子性吗？","考察边界",null,null,null,null,
          TurnProvenance.initial(),adopted ? List.of(rubric) : List.of());
      var session=AdaptiveInterviewSession.create("s",plan.maxTurns(),EVALUATION_SETTINGS);
      var history=new AdaptiveInterviewHistory(session,"candidate","","","provider",List.of(turn));
      var interview=new PlannedInterview(history,plan,List.of());
      var captured=new AtomicReference<AssessmentRequest>();
      var assessor=new AdaptiveAnswerAssessmentService(new DepthAssessmentAgent((r,p) -> {
        captured.set(r);
        return new AssessmentProposal(DepthLevel.L1,0.8,"识别原子性边界",List.of("不能"));
      }),new AssessmentEvidenceValidator(),mock(InterviewSkillService.class));
      assessor.assess(interview,new CandidateAnswer(1,"不能保证复合操作原子性"));
      assertThat(captured.get().context().rubric()).hasSize(5);
      assertThat(captured.get().context().adoptedRubrics()).hasSize(adopted ? 1 : 0);
      if(adopted) assertThat(captured.get().context().adoptedRubrics().getFirst().body())
          .isEqualTo("volatile 不保证 i++ 原子性");
    }
  }
}
