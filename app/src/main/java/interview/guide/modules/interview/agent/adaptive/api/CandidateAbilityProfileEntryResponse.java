package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.assessment.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.memory.CandidateAbilityProfile;
import java.time.LocalDateTime;

/**
 * 候选人能力画像条目响应。
 */
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
