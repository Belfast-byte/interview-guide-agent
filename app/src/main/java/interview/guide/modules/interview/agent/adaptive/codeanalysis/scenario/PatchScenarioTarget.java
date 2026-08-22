package interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario;

/**
 * 补丁场景目标。
 */
public record PatchScenarioTarget(
    String scenarioId,
    String workspaceRef,
    String testsRef
) {}
