package interview.guide.modules.interview.agent.adaptive.memory.profile;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import java.time.LocalDateTime;

/**
 * 候选人能力画像。
 */
public record CandidateAbilityProfile(
    String dimension,
    DepthLevel depthLevel,
    String sourceSessionId,
    Long sourceAssessmentId,
    boolean current,
    LocalDateTime assessedAt
) {}
