package interview.guide.modules.interview.agent.adaptive.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts.AssessmentFact;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts.EvidenceFact;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts.ProbeGapFact;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts.TurnFact;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoverageProjectorTest {

  @Test
  @DisplayName("按事实投影全部 Target、Gap 与 Evidence 且不替模型排序")
  void shouldProjectNeutralCoverage() {
    CapabilityTarget first = target(0, "JVM");
    CapabilityTarget second = target(1, "Redis");
    CoverageView coverage = CoverageProjector.project(new CoverageFacts(
        4,
        List.of(first, second),
        List.of(
            new TurnFact(1, "target-1"),
            new TurnFact(2, "target-1"),
            new TurnFact(3, "target-0")
        ),
        List.of(
            new AssessmentFact(10, 1, "target-1", DepthLevel.L3),
            new AssessmentFact(11, 2, "target-1", DepthLevel.L2),
            new AssessmentFact(12, 3, "target-0", DepthLevel.L1)
        ),
        List.of(
            new ProbeGapFact(20, 10, "anchor", "open", null),
            new ProbeGapFact(21, 12, "anchor", "closed", 13L)
        ),
        List.of(new EvidenceFact(30, 12), new EvidenceFact(31, 10))
    ));

    assertThat(coverage.askedTurns()).isEqualTo(3);
    assertThat(coverage.remainingTurns()).isEqualTo(1);
    assertThat(coverage.targets()).extracting(target -> target.target().identity().dimension())
        .containsExactly("JVM", "Redis");
    assertThat(coverage.targets().get(1)).satisfies(target -> {
      assertThat(target.askedTurns()).isEqualTo(2);
      assertThat(target.latestDepth()).isEqualTo(DepthLevel.L2);
      assertThat(target.openGapIds()).containsExactly(20L);
      assertThat(target.evidenceIds()).containsExactly(31L);
    });
    assertThat(coverage.openProbeGaps()).extracting(CoverageView.OpenProbeGap::gapId)
        .containsExactly(20L);
  }

  private CapabilityTarget target(int order, String dimension) {
    return new CapabilityTarget(
        new CapabilityTarget.Identity(
            order,
            dimension,
            dimension + " focus",
            new TopicKey("java-backend", dimension.toLowerCase())
        ),
        new CapabilityTarget.Budget(2, 2, 1, 0),
        new CapabilityTarget.Depth(DepthLevel.L2, DepthLevel.L3),
        List.of(),
        List.of()
    );
  }
}
