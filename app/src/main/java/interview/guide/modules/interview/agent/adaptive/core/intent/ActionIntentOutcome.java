package interview.guide.modules.interview.agent.adaptive.core.intent;

/** 成功结果或显式失败现场。 */
public record ActionIntentOutcome(
    ActionResultType resultType,
    String resultRef,
    String error
) {

  public static ActionIntentOutcome none() {
    return new ActionIntentOutcome(null, null, null);
  }

  public static ActionIntentOutcome succeeded(ActionResultType type, String resultRef) {
    return new ActionIntentOutcome(type, resultRef, null);
  }

  public static ActionIntentOutcome failed(String error) {
    return new ActionIntentOutcome(null, null, error);
  }
}
