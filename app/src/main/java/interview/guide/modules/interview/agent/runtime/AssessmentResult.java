package interview.guide.modules.interview.agent.runtime;

public record AssessmentResult(
    AnswerDepthLevel depth,
    AnswerEvidence evidence,
    AssessmentAction suggestedAction
) {}
