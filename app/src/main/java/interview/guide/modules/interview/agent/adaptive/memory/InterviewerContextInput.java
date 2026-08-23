package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.EpisodePromptFact;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.ProjectInterviewContext;
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
    List<ProbeGap> currentAnswerGaps,
    List<EpisodePromptFact> episodeHistory,
    ProjectInterviewContext project
) {

  public InterviewerContextInput {
    suggestedTools = List.copyOf(suggestedTools);
    turns = List.copyOf(turns);
    currentAnswerGaps = List.copyOf(currentAnswerGaps);
    episodeHistory = List.copyOf(episodeHistory);
  }
}
