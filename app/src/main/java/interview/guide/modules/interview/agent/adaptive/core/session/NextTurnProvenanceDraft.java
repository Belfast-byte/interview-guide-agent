package interview.guide.modules.interview.agent.adaptive.core.session;

import java.util.Map;

/**
 * 下一题生成阶段的 provenance 草案。当前评估尚未落库时只记录 gapOrder，
 * 由短事务保存 gaps 后解析 Assessment 与 ProbeGap 的真实 ID。
 */
public record NextTurnProvenanceDraft(
    Integer parentTurnIndex,
    AssessmentGapSource assessmentGapSource
) {

  public NextTurnProvenanceDraft {
    if (parentTurnIndex != null && parentTurnIndex < 1) {
      throw new IllegalArgumentException("父轮次必须为正数");
    }
    if (parentTurnIndex == null && assessmentGapSource != null) {
      throw new IllegalArgumentException("Assessment 来源必须携带父轮次");
    }
    if (parentTurnIndex != null && assessmentGapSource == null) {
      throw new IllegalArgumentException("追问父轮次必须携带 gap 来源");
    }
  }

  public static NextTurnProvenanceDraft planned() {
    return new NextTurnProvenanceDraft(null, null);
  }

  public static NextTurnProvenanceDraft currentAssessmentGap(
      int parentTurnIndex,
      int gapOrder
  ) {
    return new NextTurnProvenanceDraft(
        parentTurnIndex,
        new CurrentAssessmentGap(gapOrder)
    );
  }

  public static NextTurnProvenanceDraft persistedAssessmentGap(
      int parentTurnIndex,
      long assessmentId,
      long probeGapId
  ) {
    return new NextTurnProvenanceDraft(
        parentTurnIndex,
        new PersistedAssessmentGap(assessmentId, probeGapId)
    );
  }

  public TurnProvenance resolve(
      long currentAssessmentId,
      Map<Integer, Long> currentProbeGapIds
  ) {
    if (parentTurnIndex == null) {
      return TurnProvenance.initial();
    }
    if (assessmentGapSource instanceof PersistedAssessmentGap persisted) {
      return TurnProvenance.assessmentGap(
          parentTurnIndex,
          persisted.assessmentId(),
          persisted.probeGapId()
      );
    }
    CurrentAssessmentGap current = (CurrentAssessmentGap) assessmentGapSource;
    Long probeGapId = currentProbeGapIds.get(current.gapOrder());
    if (probeGapId == null) {
      throw new IllegalArgumentException("当前 Assessment 缺少选中的 ProbeGap");
    }
    return TurnProvenance.assessmentGap(parentTurnIndex, currentAssessmentId, probeGapId);
  }

  /** 下一题引用的 Assessment gap 来源。 */
  public sealed interface AssessmentGapSource
      permits CurrentAssessmentGap, PersistedAssessmentGap {}

  public record CurrentAssessmentGap(int gapOrder) implements AssessmentGapSource {

    public CurrentAssessmentGap {
      if (gapOrder < 1) {
        throw new IllegalArgumentException("gapOrder 必须为正数");
      }
    }
  }

  public record PersistedAssessmentGap(
      long assessmentId,
      long probeGapId
  ) implements AssessmentGapSource {

    public PersistedAssessmentGap {
      if (assessmentId < 1 || probeGapId < 1) {
        throw new IllegalArgumentException("Assessment 与 ProbeGap ID 必须为正数");
      }
    }
  }
}
