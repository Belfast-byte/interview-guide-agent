package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.core.CandidateClaimType;

/**
 * 候选人声明。
 */
public record CandidateClaim(
    CandidateClaimType type,
    String skillId,
    String focusId,
    int sourceTurnIndex
) {}
