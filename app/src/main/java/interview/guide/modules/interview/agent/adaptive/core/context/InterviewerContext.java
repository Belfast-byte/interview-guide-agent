package interview.guide.modules.interview.agent.adaptive.core.context;

import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;

import java.util.List;

/**
 * 面试官上下文，包含当前维度、历史、记忆和可用工具，用于生成下一轮问题。
 */
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
    List<ProbeGap> currentAnswerGaps,
    List<DimensionBrief> completedDimensionBriefs,
    ToolResultEvent currentToolResult,
    CandidateAnswer currentCodeSubmission,
    ProjectInterviewContext project
) {

  public InterviewerContext {
    suggestedTools = List.copyOf(suggestedTools);
    currentDimensionTurns = List.copyOf(currentDimensionTurns);
    currentAnswerGaps = List.copyOf(currentAnswerGaps);
    completedDimensionBriefs = List.copyOf(completedDimensionBriefs);
  }

}
