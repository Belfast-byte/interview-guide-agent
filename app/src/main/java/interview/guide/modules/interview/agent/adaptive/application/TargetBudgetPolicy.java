package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.context.CoverageProjector;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 将 Target 预算耗尽投影为供 Agent 自主切换模块的 Observation。 */
@Component
public class TargetBudgetPolicy {

  private static final int BUDGET_MULTIPLIER_NUMERATOR = 5;
  private static final int BUDGET_MULTIPLIER_DENOMINATOR = 4;

  public BudgetDecision evaluate(CoverageView coverage, AnswerAssessment assessment) {
    String targetId = CoverageProjector.targetId(assessment.dimension().order());
    CoverageView.TargetCoverage target = coverage.targets().stream()
        .filter(candidate -> candidate.targetId().equals(targetId))
        .findFirst()
        .orElseThrow();
    int allocatedTurns = target.target().budget().turnBudget();
    int limit = ceilingBudget(allocatedTurns);
    boolean exhausted = target.askedTurns() >= limit
        && !assessment.decision().probeGaps().isEmpty();
    if (!exhausted) {
      return new BudgetDecision(false, targetId, List.of());
    }
    return new BudgetDecision(true, targetId, List.of(observation(
        targetId, target.askedTurns(), allocatedTurns)));
  }

  private int ceilingBudget(int allocatedTurns) {
    int scaled = allocatedTurns * BUDGET_MULTIPLIER_NUMERATOR;
    return (scaled + BUDGET_MULTIPLIER_DENOMINATOR - 1)
        / BUDGET_MULTIPLIER_DENOMINATOR;
  }

  private DecisionObservation observation(
      String targetId,
      int askedTurns,
      int allocatedTurns
  ) {
    return new DecisionObservation(
        "budget-exhausted-" + targetId,
        DecisionObservation.Kind.BUDGET_EXHAUSTED,
        "coverage.targets[" + targetId + "]",
        "当前 Target 已达到追问预算上限，请切换到其他尚未充分覆盖的 Target",
        null,
        Map.of(
            "targetId", targetId,
            "askedTurns", askedTurns,
            "allocatedTurns", allocatedTurns
        ),
        List.of()
    );
  }

  public record BudgetDecision(
      boolean exhausted,
      String targetId,
      List<DecisionObservation> observations
  ) {

    public BudgetDecision {
      observations = List.copyOf(observations);
    }
  }
}
