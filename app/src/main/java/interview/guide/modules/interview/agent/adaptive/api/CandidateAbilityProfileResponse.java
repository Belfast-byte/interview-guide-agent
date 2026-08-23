package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileSnapshot;
import java.util.List;

/**
 * 候选人能力画像响应。
 */
public record CandidateAbilityProfileResponse(
    String candidateId,
    List<CandidateAbilityProfileEntryResponse> trajectory
) {

  static CandidateAbilityProfileResponse from(
      String candidateId,
      List<AbilityProfileSnapshot> trajectory
  ) {
    return new CandidateAbilityProfileResponse(
        candidateId,
        trajectory.stream().map(CandidateAbilityProfileEntryResponse::from).toList()
    );
  }
}
