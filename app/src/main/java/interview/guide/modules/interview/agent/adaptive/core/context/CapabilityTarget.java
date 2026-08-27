package interview.guide.modules.interview.agent.adaptive.core.context;

import java.util.List;

/** Planner 提案经代码裁决后的单个能力目标。 */
public record CapabilityTarget(
    Identity identity,
    Budget budget,
    Depth depth,
    List<EvidenceObjective> evidenceObjectives,
    List<String> suggestedTools
) {

  public CapabilityTarget {
    evidenceObjectives = List.copyOf(evidenceObjectives);
    suggestedTools = List.copyOf(suggestedTools);
  }

  public CapabilityTarget withTurnBudget(int turnBudget) {
    return new CapabilityTarget(
        identity,
        budget.withTurnBudget(turnBudget),
        depth,
        evidenceObjectives,
        suggestedTools
    );
  }

  public record Identity(
      int order,
      String dimension,
      String focus,
      TopicKey topic
  ) {}

  public record Budget(
      int suggestedTurns,
      int turnBudget,
      int followUpBudget,
      int toolBudget
  ) {

    Budget withTurnBudget(int nextTurnBudget) {
      return new Budget(suggestedTurns, nextTurnBudget, followUpBudget, toolBudget);
    }
  }

  public record Depth(DepthLevel expected, DepthLevel ceiling) {}

  public record EvidenceObjective(String description, EvidenceMethod method) {}

  public enum EvidenceMethod {
    CANDIDATE_ANSWER,
    TOOL_FACT
  }
}
