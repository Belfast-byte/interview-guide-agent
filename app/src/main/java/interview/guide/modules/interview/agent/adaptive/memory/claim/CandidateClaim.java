package interview.guide.modules.interview.agent.adaptive.memory.claim;

import interview.guide.modules.interview.agent.adaptive.core.context.CandidateClaimType;

/**
 * 候选人声明。
 */
public record CandidateClaim(
    CandidateClaimType type,
    String skillId,
    String focusId,
    int sourceTurnIndex
) {}
