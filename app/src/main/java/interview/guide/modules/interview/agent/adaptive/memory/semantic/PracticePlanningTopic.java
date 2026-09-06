package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.List;

public record PracticePlanningTopic(
    TopicKey topic,
    PracticePlanningStatus status,
    List<StablePattern> stablePatterns,
    List<interview.guide.modules.interview.agent.adaptive.memory.observation.CapabilityBelief> beliefs
) {

  public PracticePlanningTopic(TopicKey topic,PracticePlanningStatus status,List<StablePattern> stablePatterns) {
    this(topic,status,stablePatterns,List.of());
  }
  public PracticePlanningTopic {
    stablePatterns = List.copyOf(stablePatterns);
    beliefs = List.copyOf(beliefs);
  }
}
