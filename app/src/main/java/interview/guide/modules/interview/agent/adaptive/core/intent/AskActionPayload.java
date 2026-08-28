package interview.guide.modules.interview.agent.adaptive.core.intent;

public record AskActionPayload(
    ActionTarget target,
    String idempotencyKey,
    AskActionContext context
) implements ActionIntentPayload {

  @Override
  public ActionIntentType type() {
    return ActionIntentType.ASK;
  }

  public interview.guide.modules.interview.agent.adaptive.core.session.NextTurnProvenanceDraft
      provenance() {
    return context.provenance();
  }
}
