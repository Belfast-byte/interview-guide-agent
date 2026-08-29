package interview.guide.modules.interview.agent.adaptive.core.context;

import java.util.List;

/** Agent 在相邻 Turn 之间保留的短期注意力，只保存引用和短期认知。 */
public record WorkingMemory(
    Integer basedOnTurnIndex,
    Focus focus,
    Deliberation deliberation
) {

  public static WorkingMemory empty() {
    return new WorkingMemory(
        null,
        new Focus(null, null, List.of()),
        new Deliberation(List.of(), null, List.of())
    );
  }

  public record Focus(
      String activeTargetId,
      Long activeGapId,
      List<GapPriority> gapPriorities
  ) {

    public Focus {
      gapPriorities = List.copyOf(gapPriorities);
    }
  }

  public record Deliberation(
      List<Hypothesis> hypotheses,
      String nextProbeIntent,
      List<String> adoptedObservationRefs
  ) {

    public Deliberation {
      hypotheses = List.copyOf(hypotheses);
      adoptedObservationRefs = List.copyOf(adoptedObservationRefs);
    }
  }

  public record GapPriority(long gapId, String reason) {}

  public record Hypothesis(
      String statement,
      String status,
      EvidenceLinks evidenceLinks
  ) {}

  public record EvidenceLinks(
      List<Long> supportingEvidenceIds,
      List<Long> contradictingEvidenceIds
  ) {

    public EvidenceLinks {
      supportingEvidenceIds = List.copyOf(supportingEvidenceIds);
      contradictingEvidenceIds = List.copyOf(contradictingEvidenceIds);
    }
  }
}
