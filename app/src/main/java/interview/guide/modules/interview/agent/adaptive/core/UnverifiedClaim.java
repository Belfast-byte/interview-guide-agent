package interview.guide.modules.interview.agent.adaptive.core;

public record UnverifiedClaim(
    CandidateClaimType type,
    String skillId,
    String focusId
) {}
