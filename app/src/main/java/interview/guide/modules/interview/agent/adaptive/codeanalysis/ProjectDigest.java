package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.List;

/**
 * 项目摘要。
 */
public record ProjectDigest(
    String digestId,
    String commitHash,
    List<String> stack,
    List<ProjectModule> modules,
    List<ProjectFinding> highlightCandidates,
    List<ProjectFinding> riskSpots
) {

  public record ProjectModule(String name, String role, CodeAnchor anchor) {}

  public record ProjectFinding(String title, CodeAnchor anchor, String why) {}
}
