package interview.guide.modules.interview.agent.adaptive.core.context;

import interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts.AssessmentFact;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts.EvidenceFact;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts.ProbeGapFact;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts.TurnFact;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView.OpenProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView.TargetCoverage;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Projects coverage without choosing the next target, gap, tool, or action. */
public final class CoverageProjector {

  private static final String TARGET_PREFIX = "target-";

  private CoverageProjector() {}

  public static CoverageView project(CoverageFacts facts) {
    Map<Long, AssessmentFact> assessments = facts.assessments().stream()
        .collect(Collectors.toMap(AssessmentFact::assessmentId, Function.identity()));
    List<OpenProbeGap> openGaps = openGaps(facts.probeGaps(), assessments);
    ProjectionIndex index = new ProjectionIndex(facts, assessments, openGaps);
    List<TargetCoverage> targets = facts.targets().stream()
        .map(target -> targetCoverage(target, index))
        .toList();
    return new CoverageView(
        facts.turns().size(),
        Math.max(0, facts.maxTurns() - facts.turns().size()),
        targets,
        openGaps,
        facts.evidences().stream().map(EvidenceFact::evidenceId).toList()
    );
  }

  public static String targetId(int order) {
    return TARGET_PREFIX + order;
  }

  private static TargetCoverage targetCoverage(
      CapabilityTarget target,
      ProjectionIndex index
  ) {
    String targetId = targetId(target.identity().order());
    List<AssessmentFact> targetAssessments = index.facts().assessments().stream()
        .filter(assessment -> assessment.targetId().equals(targetId))
        .toList();
    return new TargetCoverage(
        targetId,
        target,
        (int) index.facts().turns().stream()
            .filter(turn -> turn.targetId().equals(targetId)).count(),
        latestDepth(targetAssessments),
        index.openGaps().stream().filter(gap -> gap.targetId().equals(targetId))
            .map(OpenProbeGap::gapId).toList(),
        index.facts().evidences().stream()
            .filter(evidence -> belongsTo(evidence, targetId, index.assessments()))
            .map(EvidenceFact::evidenceId).toList()
    );
  }

  private static List<OpenProbeGap> openGaps(
      List<ProbeGapFact> gaps,
      Map<Long, AssessmentFact> assessments
  ) {
    return gaps.stream()
        .filter(gap -> gap.closedByAssessmentId() == null)
        .map(gap -> toOpenGap(gap, assessments.get(gap.assessmentId())))
        .toList();
  }

  private static OpenProbeGap toOpenGap(ProbeGapFact gap, AssessmentFact assessment) {
    return new OpenProbeGap(
        gap.gapId(),
        gap.assessmentId(),
        assessment.targetId(),
        assessment.turnIndex(),
        gap.anchor(),
        gap.description()
    );
  }

  private static boolean belongsTo(
      EvidenceFact evidence,
      String targetId,
      Map<Long, AssessmentFact> assessments
  ) {
    AssessmentFact assessment = assessments.get(evidence.assessmentId());
    return assessment != null && assessment.targetId().equals(targetId);
  }

  private static DepthLevel latestDepth(List<AssessmentFact> assessments) {
    return assessments.stream()
        .max(Comparator.comparingInt(AssessmentFact::turnIndex))
        .map(AssessmentFact::depth)
        .orElse(null);
  }

  private record ProjectionIndex(
      CoverageFacts facts,
      Map<Long, AssessmentFact> assessments,
      List<OpenProbeGap> openGaps
  ) {}
}
