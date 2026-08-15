package interview.guide.modules.interview.agent.adaptive.codeanalysis;

public record ScenarioCard(
    String scenarioId,
    String title,
    String context,
    CodeAnchor anchor,
    ScenarioTaskType taskType,
    String constraints,
    String testsRef
) {}
