package interview.guide.modules.interview.agent.adaptive.core;

/**
 * 候选人未验证声明，后续可通过代码分析或追问验证。
 */
public record UnverifiedClaim(
    CandidateClaimType type,
    String skillId,
    String focusId
) {}
