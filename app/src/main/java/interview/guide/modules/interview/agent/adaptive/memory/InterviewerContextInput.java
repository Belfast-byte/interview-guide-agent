package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerWorkView;
import interview.guide.modules.interview.agent.adaptive.core.context.ProjectInterviewContext;
import interview.guide.modules.interview.agent.adaptive.core.context.PracticeCoachingContext;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import java.util.List;

/** 面试官上下文装配输入。 */
public record InterviewerContextInput(
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
    InterviewerWorkView working,
    ProjectInterviewContext project,
    PracticeCoachingContext practiceMemory
) {

  public InterviewerContextInput {
    suggestedTools = List.copyOf(suggestedTools);
    turns = List.copyOf(turns);
  }
}
