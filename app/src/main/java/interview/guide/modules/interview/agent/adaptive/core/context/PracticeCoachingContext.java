package interview.guide.modules.interview.agent.adaptive.core.context;

import java.util.List;
import java.util.Map;

/** 练习模式下，Target 固定后交给 Interviewer 的只读长期记忆投影。 */
public record PracticeCoachingContext(
    Map<String, Object> semantic,
    List<Map<String, Object>> episodes
) {

  public PracticeCoachingContext {
    episodes = List.copyOf(episodes);
  }
}
