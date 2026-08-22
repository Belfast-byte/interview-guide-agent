package interview.guide.modules.interview.agent.adaptive.assessment.backfill;

import interview.guide.modules.interview.agent.adaptive.core.context.CodeFactUsage;

/**
 * 评估回填的单轮数据。
 */
public record AssessmentBackfillTurn(
    String sessionId,
    int turnIndex,
    int dimensionOrder,
    String dimension,
    String focus,
    String question,
    String answer,
    String llmProvider,
    String codeSourceId,
    String codeAnchor,
    CodeFactUsage codeFactUsage,
    String toolResult
) {}
