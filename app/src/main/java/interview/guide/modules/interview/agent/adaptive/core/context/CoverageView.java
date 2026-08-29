package interview.guide.modules.interview.agent.adaptive.core.context;

import java.util.List;

/** Neutral projection of facts already established in the current interview. */
public record CoverageView(
    int askedTurns,
    int remainingTurns,
    List<TargetCoverage> targets,
    List<OpenProbeGap> openProbeGaps,
    List<Long> evidenceIds
) {

  public CoverageView {
    targets = List.copyOf(targets);
    openProbeGaps = List.copyOf(openProbeGaps);
    evidenceIds = List.copyOf(evidenceIds);
  }

  public record TargetCoverage(
      String targetId,
      CapabilityTarget target,
      int askedTurns,
      DepthLevel latestDepth,
      List<Long> openGapIds,
      List<Long> evidenceIds
  ) {

    public TargetCoverage {
      openGapIds = List.copyOf(openGapIds);
      evidenceIds = List.copyOf(evidenceIds);
    }
  }

  public record OpenProbeGap(
      long gapId,
      long assessmentId,
      String targetId,
      int sourceTurnIndex,
      String anchor,
      String description
  ) {}
}
