package interview.guide.modules.interview.agent.adaptive.planning;

import java.util.List;

/**
 * 项目规划上下文，包含项目代码相关事实。
 */
public record ProjectPlanningContext(
    String digestId,
    String commitHash,
    List<String> stack,
    List<ProjectModule> modules,
    List<ProjectFinding> highlightCandidates,
    List<ProjectFinding> riskSpots
) {

  public ProjectPlanningContext {
    stack = List.copyOf(stack);
    modules = List.copyOf(modules);
    highlightCandidates = List.copyOf(highlightCandidates);
    riskSpots = List.copyOf(riskSpots);
  }

  public record ProjectModule(String name, String role, String anchor) {}

  public record ProjectFinding(String title, String anchor, String why) {}
}
