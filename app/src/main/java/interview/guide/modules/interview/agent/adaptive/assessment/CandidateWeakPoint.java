package interview.guide.modules.interview.agent.adaptive.assessment;

public record CandidateWeakPoint(
    String dimension,
    DepthLevel demonstratedLevel,
    DepthLevel missingLevel,
    String missingCapability
) {}
