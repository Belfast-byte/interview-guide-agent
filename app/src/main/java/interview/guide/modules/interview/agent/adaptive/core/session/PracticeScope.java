package interview.guide.modules.interview.agent.adaptive.core.session;

import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.List;

/** 练习会话允许规划的明确主题范围。 */
public record PracticeScope(List<TopicKey> topics) {

  public PracticeScope {
    topics = List.copyOf(topics);
  }

  public static PracticeScope none() {
    return new PracticeScope(List.of());
  }

  public boolean isEmpty() {
    return topics.isEmpty();
  }
}
