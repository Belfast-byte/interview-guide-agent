package interview.guide.modules.interview.agent.adaptive.core;

public record CodeQuestionProvenance(
    String sourceId,
    String anchor,
    CodeFactUsage usage
) {}
