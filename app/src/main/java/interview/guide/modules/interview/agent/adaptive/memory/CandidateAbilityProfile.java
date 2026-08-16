package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.assessment.DepthLevel;
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
