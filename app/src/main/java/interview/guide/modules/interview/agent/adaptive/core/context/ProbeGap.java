package interview.guide.modules.interview.agent.adaptive.core.context;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 评估 Agent 产出的中性追问点，用于告诉下一轮面试官应从回答中的哪个锚点追问什么内容。
 */
public record ProbeGap(
    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) SourceQuote anchor,
    String missingPoint
) {}
