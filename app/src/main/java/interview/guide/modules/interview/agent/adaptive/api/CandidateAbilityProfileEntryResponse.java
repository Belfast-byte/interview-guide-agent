package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileRevisionReason;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileSnapshot;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticAbility;
import java.time.LocalDateTime;

/**
 * 候选人能力画像条目响应。
 */
public record CandidateAbilityProfileEntryResponse(
    String skillId,
    String focusId,
    SemanticAbility ability,
    long l0Count,
    long l1Count,
    long l2Count,
    long l3Count,
    long l4Count,
    String sourceSessionId,
    AbilityProfileRevisionReason revisionReason,
    boolean current,
    LocalDateTime createdAt
) {

  static CandidateAbilityProfileEntryResponse from(AbilityProfileSnapshot profile) {
    return new CandidateAbilityProfileEntryResponse(
        profile.topic().skillId(),
        profile.topic().focusId(),
        profile.ability(),
        profile.counter().l0Count(),
        profile.counter().l1Count(),
        profile.counter().l2Count(),
        profile.counter().l3Count(),
        profile.counter().l4Count(),
        profile.sourceSessionId(),
        profile.revisionReason(),
        profile.current(),
        profile.createdAt()
    );
  }
}
