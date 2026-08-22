package interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnchor;

/**
 * 场景卡片。
 */
public record ScenarioCard(
    String scenarioId,
    String title,
    String context,
    CodeAnchor anchor,
    ScenarioTaskType taskType,
    String constraints,
    String testsRef
) {}
