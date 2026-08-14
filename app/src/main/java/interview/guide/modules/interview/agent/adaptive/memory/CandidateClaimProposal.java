package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.core.CandidateClaimType;

public record CandidateClaimProposal(
    CandidateClaimType type,
    String skillId,
    String focusId,
    int sourceTurnIndex
) {}
