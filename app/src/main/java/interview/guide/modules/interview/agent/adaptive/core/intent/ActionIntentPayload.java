package interview.guide.modules.interview.agent.adaptive.core.intent;

public sealed interface ActionIntentPayload permits AskActionPayload {

  ActionTarget target();

  String idempotencyKey();

  ActionIntentType type();
}
