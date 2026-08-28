package interview.guide.modules.interview.agent.adaptive.core.intent;

public record ToolActionPayload(
    ActionTarget target,
    ToolCallSpec call,
    String idempotencyKey
) implements ActionIntentPayload {

  @Override
  public ActionIntentType type() {
    return ActionIntentType.CALL_TOOL;
  }
}
