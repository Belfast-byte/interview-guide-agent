package interview.guide.modules.interview.agent.adaptive.core.context;

import java.util.List;

/** Domain facts required to project neutral interview coverage. */
public record CoverageFacts(
    int maxTurns,
    List<CapabilityTarget> targets,
    List<TurnFact> turns,
    List<AssessmentFact> assessments,
    List<ProbeGapFact> probeGaps,
    List<EvidenceFact> evidences
) {

  public CoverageFacts {
    targets = List.copyOf(targets);
    turns = List.copyOf(turns);
    assessments = List.copyOf(assessments);
    probeGaps = List.copyOf(probeGaps);
    evidences = List.copyOf(evidences);
  }

  public record TurnFact(int turnIndex, String targetId) {}

  public record AssessmentFact(
      long assessmentId,
      int turnIndex,
      String targetId,
      DepthLevel depth
  ) {}

  public record ProbeGapFact(
      long gapId,
      long assessmentId,
      String anchor,
      String description,
      Long closedByAssessmentId
  ) {}

  public record EvidenceFact(long evidenceId, long assessmentId) {}
}
