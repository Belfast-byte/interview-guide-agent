package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.EpisodePromptFact;
import interview.guide.modules.interview.agent.adaptive.core.context.ProjectInterviewContext;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemorySnapshot;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import java.util.List;

/** 工具结果触发的面试官上下文装配输入。 */
public record ToolResultContextInput(
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
    WorkingMemorySnapshot workingMemory,
    List<EpisodePromptFact> episodeHistory,
    ProjectInterviewContext project
) {

  public ToolResultContextInput {
    suggestedTools = List.copyOf(suggestedTools);
    turns = List.copyOf(turns);
    episodeHistory = List.copyOf(episodeHistory);
  }
}
