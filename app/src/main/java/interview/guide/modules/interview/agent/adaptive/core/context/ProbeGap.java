package interview.guide.modules.interview.agent.adaptive.core.context;

/**
 * 评估 Agent 产出的中性追问点，用于告诉下一轮面试官应从回答中的哪个锚点追问什么内容。
 */
public record ProbeGap(
    String anchor,
    String missingPoint
) {}
