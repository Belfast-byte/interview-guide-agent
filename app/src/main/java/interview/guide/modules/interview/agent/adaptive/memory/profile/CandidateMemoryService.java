package interview.guide.modules.interview.agent.adaptive.memory.profile;

import interview.guide.modules.interview.agent.adaptive.core.context.CoveredTopic;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.UnverifiedClaim;
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

  private final CoveredTopicSource topicSource;
  private final UnverifiedClaimSource claimSource;

  @Transactional(readOnly = true)
  public List<CoveredTopic> coveredTopics(String candidateId) {
    return topicSource.findCoveredTopics(new MemoryOwner(null, candidateId));
  }

  @Transactional(readOnly = true)
  public List<CoveredTopic> coveredTopics(String tenantId, String candidateId) {
    return topicSource.findCoveredTopics(new MemoryOwner(tenantId, candidateId));
  }

  @Transactional(readOnly = true)
  public List<UnverifiedClaim> unverifiedClaims(String candidateId) {
    return claimSource.findUnverifiedClaims(new MemoryOwner(null, candidateId));
  }

  @Transactional(readOnly = true)
  public List<UnverifiedClaim> unverifiedClaims(String tenantId, String candidateId) {
    return claimSource.findUnverifiedClaims(new MemoryOwner(tenantId, candidateId));
  }
}
