package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.List;

/**
 * 评估 Agent 输出的原始建议。
 */
public record AssessmentProposal(
    DepthLevel depthLevel,
    double confidence,
    String rationaleSummary,
    boolean recommendSwitchQuestion,
    List<String> evidenceQuotes
) {}
