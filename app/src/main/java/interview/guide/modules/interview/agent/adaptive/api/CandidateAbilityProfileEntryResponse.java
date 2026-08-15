package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.assessment.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.memory.CandidateAbilityProfile;
import java.time.LocalDateTime;

public record CandidateAbilityProfileEntryResponse(
    String dimension,
    DepthLevel depthLevel,
    String sourceSessionId,
    Long sourceAssessmentId,
    boolean current,
    LocalDateTime assessedAt
) {

  static CandidateAbilityProfileEntryResponse from(CandidateAbilityProfile profile) {
    return new CandidateAbilityProfileEntryResponse(
        profile.dimension(),
        profile.depthLevel(),
        profile.sourceSessionId(),
        profile.sourceAssessmentId(),
        profile.current(),
        profile.assessedAt()
    );
  }
}
