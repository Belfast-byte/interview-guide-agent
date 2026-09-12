package interview.guide.modules.interview.agent.adaptive.core.context;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/** Agent 在相邻 Turn 之间保留的短期注意力，只保存引用和短期认知。 */
public record WorkingMemory(
    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer basedOnTurnIndex,
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

  /** 本题 ASK 实际采用的稳定素材引用随 Turn 保存；不把前题来源计作本题来源。 */
  public WorkingMemory withAdoptedSources(List<String> sources) {
    var references = sources.stream()
        .filter(ref -> ref.startsWith("episode:") || ref.startsWith("question:")
            || ref.startsWith("rubric:")).distinct().toList();
    return new WorkingMemory(basedOnTurnIndex, focus,
        new Deliberation(deliberation.hypotheses(), deliberation.nextProbeIntent(), references));
  }

  public record Focus(
      @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String activeTargetId,
      @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long activeGapId,
      List<GapPriority> gapPriorities
  ) {

    public Focus {
      gapPriorities = gapPriorities == null ? null : Collections.unmodifiableList(new ArrayList<>(gapPriorities));
    }
  }

  public record Deliberation(
      List<Hypothesis> hypotheses,
      @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String nextProbeIntent,
      List<String> adoptedObservationRefs
  ) {

    public Deliberation {
      hypotheses = hypotheses == null ? null : Collections.unmodifiableList(new ArrayList<>(hypotheses));
      adoptedObservationRefs = adoptedObservationRefs == null ? null : Collections.unmodifiableList(new ArrayList<>(adoptedObservationRefs));
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
      supportingEvidenceIds = supportingEvidenceIds == null ? null : Collections.unmodifiableList(new ArrayList<>(supportingEvidenceIds));
      contradictingEvidenceIds = contradictingEvidenceIds == null ? null : Collections.unmodifiableList(new ArrayList<>(contradictingEvidenceIds));
    }
  }
}
