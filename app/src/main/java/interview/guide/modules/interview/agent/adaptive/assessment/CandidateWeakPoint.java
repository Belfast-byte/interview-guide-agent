package interview.guide.modules.interview.agent.adaptive.assessment;

/**
 * 候选人薄弱点。
 */
public record CandidateWeakPoint(
    String dimension,
    DepthLevel demonstratedLevel,
    DepthLevel missingLevel,
    String missingCapability
) {}
