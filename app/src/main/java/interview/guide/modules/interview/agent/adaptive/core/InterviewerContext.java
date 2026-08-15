package interview.guide.modules.interview.agent.adaptive.core;

import java.util.List;

public record InterviewerContext(
    String jd,
    String resume,
    int currentTurn,
    int maxTurns,
    int targetDimensionOrder,
    String targetDimension,
    String targetFocus,
    List<String> suggestedTools,
    String suggestedSkill,
    List<AdaptiveInterviewTurn> currentDimensionTurns,
    CandidateAnswer currentDimensionAnswer,
    List<DimensionBrief> completedDimensionBriefs,
    ToolResultEvent currentToolResult,
    CandidateAnswer currentCodeSubmission
) {

  public InterviewerContext(
      String jd,
      String resume,
      int currentTurn,
      int maxTurns,
      int targetDimensionOrder,
      String targetDimension,
      String targetFocus,
      List<String> suggestedTools,
      String suggestedSkill,
      List<AdaptiveInterviewTurn> currentDimensionTurns,
      CandidateAnswer currentDimensionAnswer,
      List<DimensionBrief> completedDimensionBriefs
  ) {
    this(
        jd,
        resume,
        currentTurn,
        maxTurns,
        targetDimensionOrder,
        targetDimension,
        targetFocus,
        suggestedTools,
        suggestedSkill,
        currentDimensionTurns,
        currentDimensionAnswer,
        completedDimensionBriefs,
        null,
        null
    );
  }

  public InterviewerContext {
    suggestedTools = List.copyOf(suggestedTools);
    currentDimensionTurns = List.copyOf(currentDimensionTurns);
    completedDimensionBriefs = List.copyOf(completedDimensionBriefs);
  }

}
