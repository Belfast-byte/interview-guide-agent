package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.core.PlannerContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ContextAssembler {

  public PlannerContext planner(String jd, String resume) {
    return new PlannerContext(jd, resume);
  }

  public InterviewerContext interviewer(
      String jd,
      String resume,
      int maxTurns,
      int targetDimensionOrder,
      String targetDimension,
      String targetFocus,
      List<String> suggestedTools,
      String suggestedSkill,
      List<AdaptiveInterviewTurn> turns,
      CandidateAnswer candidateAnswer
  ) {
    List<AdaptiveInterviewTurn> currentDimensionTurns = turns.stream()
        .filter(turn -> turn.dimensionOrder() == targetDimensionOrder)
        .toList();
    CandidateAnswer currentDimensionAnswer = candidateAnswer;
    if (candidateAnswer != null
        && turns.get(candidateAnswer.turnIndex() - 1).dimensionOrder() != targetDimensionOrder) {
      currentDimensionAnswer = null;
    }
    return new InterviewerContext(
        jd,
        resume,
        turns.size(),
        maxTurns,
        targetDimensionOrder,
        targetDimension,
        targetFocus,
        suggestedTools,
        suggestedSkill,
        currentDimensionTurns,
        currentDimensionAnswer
    );
  }
}
