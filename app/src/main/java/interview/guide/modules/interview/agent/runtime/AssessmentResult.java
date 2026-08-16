package interview.guide.modules.interview.agent.runtime;

/**
 * 回答评估结果，包含深度等级、证据和建议动作。
 */
public record AssessmentResult(
    AnswerDepthLevel depth,
    AnswerEvidence evidence,
    AssessmentAction suggestedAction
) {}
