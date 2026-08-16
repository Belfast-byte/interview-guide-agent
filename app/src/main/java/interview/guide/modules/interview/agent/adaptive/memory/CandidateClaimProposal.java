package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.core.CandidateClaimType;

/**
 * 候选人声明建议。
 */
public record CandidateClaimProposal(
    CandidateClaimType type,
    String skillId,
    String focusId,
    int sourceTurnIndex
) {}
