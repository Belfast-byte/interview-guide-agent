package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.List;

/**
 * 项目面试上下文，聚合候选人项目代码与代码分析结果。
 */
public record ProjectInterviewContext(
    String digestId,
    List<ProjectClaim> claims,
    List<ProjectScenario> scenarios
) {

  public ProjectInterviewContext {
    claims = List.copyOf(claims);
    scenarios = List.copyOf(scenarios);
  }

  public record ProjectClaim(
      String claimId,
      String claim,
      String status,
      List<ProjectCodeFact> codeFacts
  ) {

    public ProjectClaim {
      codeFacts = List.copyOf(codeFacts);
    }
  }

  public record ProjectCodeFact(String finding, String anchor) {}

  public record ProjectScenario(
      String scenarioId,
      String title,
      String context,
      String anchor,
      String taskType,
      String constraints,
      String testsRef
  ) {}
}
