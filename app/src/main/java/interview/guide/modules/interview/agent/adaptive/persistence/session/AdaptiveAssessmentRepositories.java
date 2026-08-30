package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 回答最终事务中的 Assessment/Evidence/Gap Repository 组合。 */
@Component
class AdaptiveAssessmentRepositories {

  private final AdaptiveAgentAssessmentRepository assessments;
  private final AssessmentProbeGapRepository gaps;
  private final AdaptiveAgentEvidenceRepository evidences;

  AdaptiveAssessmentRepositories(
      AdaptiveAgentAssessmentRepository assessments,
      AssessmentProbeGapRepository gaps,
      AdaptiveAgentEvidenceRepository evidences
  ) {
    this.assessments = assessments;
    this.gaps = gaps;
    this.evidences = evidences;
  }

  Optional<AdaptiveAgentAssessmentEntity> assessment(String sessionId, int turnIndex) {
    return assessments.findBySessionIdAndTurnIndex(sessionId, turnIndex);
  }

  AdaptiveAgentAssessmentEntity saveAssessment(AdaptiveAgentAssessmentEntity assessment) {
    return assessments.saveAndFlush(assessment);
  }

  List<AssessmentProbeGapEntity> saveGaps(List<AssessmentProbeGapEntity> entities) {
    return gaps.saveAllAndFlush(entities);
  }

  void closeOpenGaps(
      String sessionId,
      int dimensionOrder,
      AdaptiveAgentAssessmentEntity closingAssessment
  ) {
    gaps.findOpenForTarget(sessionId, dimensionOrder)
        .forEach(gap -> gap.closeByBudget(closingAssessment));
  }

  List<AdaptiveAgentEvidenceEntity> saveEvidences(
      List<AdaptiveAgentEvidenceEntity> entities
  ) {
    return evidences.saveAllAndFlush(entities);
  }
}
