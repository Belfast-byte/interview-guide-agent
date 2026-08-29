package interview.guide.modules.interview.agent.adaptive.core.session;

import java.util.Objects;

/**
 * 问题与触发事实的因果关系。
 */
public record TurnProvenance(
    Integer parentTurnIndex,
    TurnTrigger trigger
) {

  public TurnProvenance {
    Objects.requireNonNull(trigger, "trigger 不能为空");
    if (parentTurnIndex != null && parentTurnIndex < 1) {
      throw new IllegalArgumentException("父轮次必须为正数");
    }
    boolean initial = trigger.type() == TurnTriggerType.PLANNED;
    if (initial != (parentTurnIndex == null)) {
      throw new IllegalArgumentException("Turn trigger 与父轮次不匹配");
    }
  }

  public static TurnProvenance initial() {
    return new TurnProvenance(null, TurnTrigger.planned());
  }

  public static TurnProvenance assessmentGap(
      int parentTurnIndex,
      long assessmentId,
      long probeGapId
  ) {
    return new TurnProvenance(
        parentTurnIndex,
        TurnTrigger.assessmentGap(assessmentId, probeGapId)
    );
  }

  public static TurnProvenance agentDecision(int parentTurnIndex) {
    return new TurnProvenance(parentTurnIndex, TurnTrigger.agentDecision());
  }

  public static TurnProvenance toolResult(int parentTurnIndex, long toolResultEventId) {
    return new TurnProvenance(parentTurnIndex, TurnTrigger.toolResult(toolResultEventId));
  }

  public void validateForTurn(int turnIndex) {
    if (turnIndex < 1) {
      throw new IllegalArgumentException("轮次必须为正数");
    }
    if (parentTurnIndex != null && parentTurnIndex >= turnIndex) {
      throw new IllegalArgumentException("父轮次必须早于当前轮次");
    }
  }
}
