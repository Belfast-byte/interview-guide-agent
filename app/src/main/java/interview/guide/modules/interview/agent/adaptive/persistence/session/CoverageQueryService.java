package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts.AssessmentFact;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts.EvidenceFact;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts.ProbeGapFact;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts.TurnFact;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageProjector;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapRepository;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class CoverageQueryService {

  private final AdaptiveAgentAssessmentRepository assessmentRepository;
  private final AssessmentProbeGapRepository probeGapRepository;
  private final AdaptiveAgentEvidenceRepository evidenceRepository;

  @Transactional(readOnly = true)
  public CoverageView load(InterviewPlan plan, List<AdaptiveInterviewTurn> turns) {
    List<AssessmentFact> assessments = assessmentRepository
        .findBySessionIdOrderByDimensionOrderAscTurnIndexAsc(plan.sessionId()).stream()
        .map(assessment -> new AssessmentFact(
            assessment.id(),
            assessment.turnIndex(),
            CoverageProjector.targetId(assessment.dimensionOrder()),
            assessment.depthLevel()
        ))
        .toList();
    return CoverageProjector.project(new CoverageFacts(
        plan.maxTurns(),
        plan.dimensions().stream().map(dimension -> dimension.target()).toList(),
        turnFacts(turns),
        assessments,
        probeGapRepository.findSessionGaps(plan.sessionId()).stream()
            .map(gap -> new ProbeGapFact(
                gap.id(), gap.assessmentId(), gap.toDomain().anchor(),
                gap.toDomain().missingPoint(), null
            ))
            .toList(),
        evidenceRepository.findReportEvidence(plan.sessionId()).stream()
            .map(this::evidenceFact)
            .toList()
    ));
  }

  private List<TurnFact> turnFacts(List<AdaptiveInterviewTurn> turns) {
    return turns.stream()
        .filter(turn -> turn.dimensionOrder() != null)
        .map(turn -> new TurnFact(
            turn.turnIndex(), CoverageProjector.targetId(turn.dimensionOrder())
        ))
        .toList();
  }

  private EvidenceFact evidenceFact(AdaptiveAgentEvidenceEntity evidence) {
    return new EvidenceFact(evidence.id(), evidence.assessment().id());
  }
}
