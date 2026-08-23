package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.memory.profile.CandidateAbilityProfileSource;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileSnapshot;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL 候选人能力画像轨迹适配器。 */
@Service
@RequiredArgsConstructor
public class JpaCandidateAbilityProfileSource implements CandidateAbilityProfileSource {

  private final CandidateAbilityProfileRepository repository;

  @Override
  @Transactional(readOnly = true)
  public List<AbilityProfileSnapshot> trajectory(MemoryOwner owner) {
    if (owner.tenantId() == null) {
      return repository
          .findByTenantIdIsNullAndCandidateIdOrderByCreatedAtAscIdAsc(owner.candidateId())
          .stream()
          .map(CandidateAbilityProfileEntity::toDomain)
          .toList();
    }
    return repository
        .findByTenantIdAndCandidateIdOrderByCreatedAtAscIdAsc(
            owner.tenantId(),
            owner.candidateId()
        )
        .stream()
        .map(CandidateAbilityProfileEntity::toDomain)
        .toList();
  }
}
