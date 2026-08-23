package interview.guide.modules.interview.agent.adaptive.memory.profile;

import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileSnapshot;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateAbilityProfileRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 候选人能力画像服务，维护跨会话的能力标签和轨迹。
 */
@Service
@RequiredArgsConstructor
public class CandidateAbilityProfileService {

  private final CandidateAbilityProfileRepository repository;

  @Transactional(readOnly = true)
  public List<AbilityProfileSnapshot> trajectory(String candidateId) {
    return repository
        .findByTenantIdIsNullAndCandidateIdOrderByCreatedAtAscIdAsc(candidateId)
        .stream()
        .map(profile -> profile.toDomain())
        .toList();
  }
}
