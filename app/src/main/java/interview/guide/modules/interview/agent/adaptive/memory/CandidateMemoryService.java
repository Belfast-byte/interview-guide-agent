package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.core.CoveredTopic;
import interview.guide.modules.interview.agent.adaptive.persistence.CandidateMemoryTopicEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.CandidateMemoryTopicRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CandidateMemoryService {

  private final CandidateMemoryTopicRepository topicRepository;

  @Transactional(readOnly = true)
  public List<CoveredTopic> coveredTopics(String candidateId) {
    return topicRepository.findByCandidateIdOrderByObservedAtDesc(candidateId).stream()
        .map(CandidateMemoryTopicEntity::toDomain)
        .distinct()
        .toList();
  }
}
