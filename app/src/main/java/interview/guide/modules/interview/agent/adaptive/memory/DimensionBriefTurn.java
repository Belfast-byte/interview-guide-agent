package interview.guide.modules.interview.agent.adaptive.memory;

/**
 * 维度简报对应的轮次数据。
 */
public record DimensionBriefTurn(
    int turnIndex,
    String question,
    String answer
) {}
