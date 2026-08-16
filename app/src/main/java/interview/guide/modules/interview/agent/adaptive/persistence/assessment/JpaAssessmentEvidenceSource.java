package interview.guide.modules.interview.agent.adaptive.persistence.assessment;

import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceSource;
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmEvidenceSource;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentToolCallEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentToolCallRepository;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 基于 JPA 的评估证据来源实现。
 */
@Component
@RequiredArgsConstructor
public class JpaAssessmentEvidenceSource implements AssessmentEvidenceSource {

  private final AdaptiveAgentToolCallRepository toolCallRepository;
  private final AlgorithmEvidenceSource algorithmEvidenceSource;

  @Override
  public AssessmentEvidenceFacts load(
      String sessionId,
      int turnIndex,
      Set<String> toolResultIds
  ) {
    return new AssessmentEvidenceFacts(
        toolCallRepository.findBySessionIdAndTurnIndexAndResultIdIn(
            sessionId,
            turnIndex,
            toolResultIds
        ).stream().collect(Collectors.toMap(
            AdaptiveAgentToolCallEntity::resultId,
            AdaptiveAgentToolCallEntity::id
        )),
        algorithmEvidenceSource.findCandidateEvidenceIds(
            sessionId,
            turnIndex,
            toolResultIds
        )
    );
  }
}
