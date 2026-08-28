package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import java.util.List;

public record PracticePlanningMemory(List<PracticePlanningTopic> topics) {

  public PracticePlanningMemory {
    topics = List.copyOf(topics);
  }
}
