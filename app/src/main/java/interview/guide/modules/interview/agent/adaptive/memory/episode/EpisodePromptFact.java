package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 允许进入 Interviewer prompt 的历史 Episode 白名单投影。
 */
public record EpisodePromptFact(
    String skillId,
    String focusId,
    DepthLevel depthLevel,
    List<String> errorTags,
    List<String> answerHabitTags,
    LocalDateTime createdAt
) {

  public EpisodePromptFact {
    errorTags = List.copyOf(errorTags);
    answerHabitTags = List.copyOf(answerHabitTags);
  }
}
