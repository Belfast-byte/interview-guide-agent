package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.List;

public record PracticePlanningTopic(
    TopicKey topic,
    PracticePlanningStatus status,
    List<StablePattern> stablePatterns
) {

  public PracticePlanningTopic {
    stablePatterns = List.copyOf(stablePatterns);
  }
}
