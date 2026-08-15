package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.assessment.DepthLevel;
import java.time.LocalDateTime;

public record CandidateAbilityProfile(
    String dimension,
    DepthLevel depthLevel,
    String sourceSessionId,
    Long sourceAssessmentId,
    boolean current,
    LocalDateTime assessedAt
) {}
