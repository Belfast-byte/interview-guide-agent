package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView.TargetCoverage;
import interview.guide.modules.interview.agent.adaptive.core.memory.TargetWorkStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;

/**
 * MCP 面试维度响应。
 */
public record McpInterviewDimensionResponse(
    int order,
    String dimension,
    String focus,
    int allocatedTurns,
    int completedTurns,
    TargetWorkStatus status
) {

  static McpInterviewDimensionResponse from(
      TargetCoverage coverage,
      AdaptiveInterviewHistory history
  ) {
    var target = coverage.target();
    return new McpInterviewDimensionResponse(
        target.identity().order(),
        target.identity().dimension(),
        target.identity().focus(),
        target.budget().turnBudget(),
        coverage.askedTurns(),
        displayStatus(coverage, history)
    );
  }

  private static TargetWorkStatus displayStatus(
      TargetCoverage coverage,
      AdaptiveInterviewHistory history
  ) {
    boolean current = history.session().status() == AdaptiveSessionStatus.IN_PROGRESS
        && !history.turns().isEmpty()
        && history.turns().getLast().dimensionOrder() != null
        && history.turns().getLast().dimensionOrder() == targetOrder(coverage);
    if (current) {
      return TargetWorkStatus.ACTIVE;
    }
    return coverage.askedTurns() > 0
        ? TargetWorkStatus.COMPLETED
        : TargetWorkStatus.PENDING;
  }

  private static int targetOrder(TargetCoverage coverage) {
    return coverage.target().identity().order();
  }
}
