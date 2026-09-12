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

  void resolveGaps(String sessionId, int dimensionOrder,
      AdaptiveAgentAssessmentEntity closingAssessment,
      List<interview.guide.modules.interview.agent.adaptive.assessment.depth.GapResolution> resolutions) {
    var open = gaps.findOpenForTarget(sessionId, dimensionOrder).stream()
        .collect(java.util.stream.Collectors.toMap(AssessmentProbeGapEntity::id, g -> g));
    for (var resolution : resolutions) {
      var gap = open.remove(resolution.gapId());
      if (gap == null) throw new IllegalStateException("待关闭缺口不属于当前维度或已关闭");
      gap.closeByEvidence(closingAssessment, resolution.evidenceQuote(), resolution.reason());
    }
  }

  AssessmentProbeGapEntity openGap(String sessionId, int dimensionOrder, long gapId) {
    return gaps.findOpenForTarget(sessionId, dimensionOrder).stream()
        .filter(gap -> gap.id() == gapId).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("追问缺口不属于所选维度或已关闭"));
  }

  List<AdaptiveAgentEvidenceEntity> saveEvidences(
      List<AdaptiveAgentEvidenceEntity> entities
  ) {
    return evidences.saveAllAndFlush(entities);
  }
}
