package interview.guide.modules.interview.agent.adaptive.core.memory;

/** 下一动作策略的确定性输出。 */
public record NextAction(
    NextActionType type,
    String targetId,
    String issueId,
    String nextTargetId,
    TargetWorkStatus terminalStatus
) {

  public static NextAction simple(NextActionType type, String targetId) {
    return new NextAction(type, targetId, null, null, null);
  }
}
