package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.CoveredTopic;
import interview.guide.modules.interview.agent.adaptive.core.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.core.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.core.PlannerContext;
import interview.guide.modules.interview.agent.adaptive.core.PlanningSkill;
import interview.guide.modules.interview.agent.adaptive.core.UnverifiedClaim;
import interview.guide.modules.interview.agent.adaptive.core.ToolResultEvent;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ContextAssembler {

  public PlannerContext planner(
      String jd,
      String resume,
      List<CoveredTopic> coveredTopics,
      List<UnverifiedClaim> unverifiedClaims,
      List<PlanningSkill> skillCatalog
  ) {
    return new PlannerContext(jd, resume, coveredTopics, unverifiedClaims, skillCatalog);
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
      CandidateAnswer candidateAnswer,
      List<DimensionBrief> dimensionBriefs
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
        currentDimensionAnswer,
        dimensionBriefs.stream()
            .filter(brief -> brief.dimensionOrder() != targetDimensionOrder)
            .toList(),
        null,
        candidateAnswer != null && candidateAnswer.codeSubmission() != null
            ? candidateAnswer
            : null
    );
  }

  public InterviewerContext toolResult(
      String jd,
      String resume,
      int maxTurns,
      int targetDimensionOrder,
      String targetDimension,
      String targetFocus,
      List<String> suggestedTools,
      String suggestedSkill,
      List<AdaptiveInterviewTurn> turns,
      ToolResultEvent event,
      List<DimensionBrief> dimensionBriefs
  ) {
    return new InterviewerContext(
        jd,
        resume,
        event.turnIndex(),
        maxTurns,
        targetDimensionOrder,
        targetDimension,
        targetFocus,
        suggestedTools,
        suggestedSkill,
        turns.stream()
            .filter(turn -> turn.dimensionOrder() == targetDimensionOrder)
            .toList(),
        null,
        dimensionBriefs.stream()
            .filter(brief -> brief.dimensionOrder() != targetDimensionOrder)
            .toList(),
        event,
        null
    );
  }
}
