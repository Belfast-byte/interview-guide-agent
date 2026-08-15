package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.persistence.CandidateAbilityProfileRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CandidateAbilityProfileService {

  private final CandidateAbilityProfileRepository repository;

  @Transactional(readOnly = true)
  public List<CandidateAbilityProfile> trajectory(String candidateId) {
    return repository
        .findByTenantIdIsNullAndCandidateIdOrderByCreatedAtAscIdAsc(candidateId)
        .stream()
        .map(profile -> new CandidateAbilityProfile(
            profile.dimension(),
            profile.depthLevel(),
            profile.sourceSessionId(),
            profile.sourceAssessmentId(),
            profile.current(),
            profile.createdAt()
        ))
        .toList();
  }
}
