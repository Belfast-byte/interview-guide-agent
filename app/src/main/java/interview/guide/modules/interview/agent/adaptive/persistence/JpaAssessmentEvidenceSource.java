package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentEvidenceFacts;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentEvidenceSource;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmEvidenceSource;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaAssessmentEvidenceSource implements AssessmentEvidenceSource {

  private final AdaptiveAgentToolCallRepository toolCallRepository;
  private final AlgorithmEvidenceSource algorithmEvidenceSource;

  @Override
  @Transactional(readOnly = true)
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
