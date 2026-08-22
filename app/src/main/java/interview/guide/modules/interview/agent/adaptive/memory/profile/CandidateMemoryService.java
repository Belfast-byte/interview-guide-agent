package interview.guide.modules.interview.agent.adaptive.memory.profile;

import interview.guide.modules.interview.agent.adaptive.core.context.CoveredTopic;
import interview.guide.modules.interview.agent.adaptive.core.context.UnverifiedClaim;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryClaimEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryClaimRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryTopicEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryTopicRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 候选人记忆服务，统一读写已覆盖主题、未验证声明和能力画像。
 */
@Service
@RequiredArgsConstructor
public class CandidateMemoryService {

  private final CandidateMemoryTopicRepository topicRepository;
  private final CandidateMemoryClaimRepository claimRepository;

  @Transactional(readOnly = true)
  public List<CoveredTopic> coveredTopics(String candidateId) {
    return topicRepository
        .findByTenantIdIsNullAndCandidateIdOrderByObservedAtDesc(candidateId)
        .stream()
        .map(CandidateMemoryTopicEntity::toDomain)
        .distinct()
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CoveredTopic> coveredTopics(String tenantId, String candidateId) {
    return topicRepository
        .findByTenantIdAndCandidateIdOrderByObservedAtDesc(tenantId, candidateId)
        .stream()
        .map(CandidateMemoryTopicEntity::toDomain)
        .distinct()
        .toList();
  }

  @Transactional(readOnly = true)
  public List<UnverifiedClaim> unverifiedClaims(String candidateId) {
    return claimRepository
        .findByTenantIdIsNullAndCandidateIdOrderByObservedAtDesc(candidateId)
        .stream()
        .map(CandidateMemoryClaimEntity::toDomain)
        .distinct()
        .toList();
  }

  @Transactional(readOnly = true)
  public List<UnverifiedClaim> unverifiedClaims(String tenantId, String candidateId) {
    return claimRepository
        .findByTenantIdAndCandidateIdOrderByObservedAtDesc(tenantId, candidateId)
        .stream()
        .map(CandidateMemoryClaimEntity::toDomain)
        .distinct()
        .toList();
  }
}
