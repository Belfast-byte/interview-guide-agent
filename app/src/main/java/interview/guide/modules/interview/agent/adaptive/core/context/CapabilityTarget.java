package interview.guide.modules.interview.agent.adaptive.core.context;

import java.util.List;

/** Planner 提案经代码裁决后的单个能力目标。 */
public record CapabilityTarget(
    Identity identity,
    Budget budget,
    Depth depth,
    List<EvidenceObjective> evidenceObjectives
) {

  public CapabilityTarget {
    evidenceObjectives = List.copyOf(evidenceObjectives);
  }

  public record Identity(
      int order,
      String dimension,
      String focus,
      TopicKey topic
  ) {}

  public record Budget(
      int suggestedTurns,
      int turnBudget
  ) {
  }

  public record Depth(DepthLevel expected, DepthLevel ceiling) {}

  public record EvidenceObjective(String description, EvidenceMethod method) {}

  public enum EvidenceMethod {
    CANDIDATE_ANSWER
  }
}
