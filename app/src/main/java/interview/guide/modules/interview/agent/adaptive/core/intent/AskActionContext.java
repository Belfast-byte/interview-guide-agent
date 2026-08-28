package interview.guide.modules.interview.agent.adaptive.core.intent;

import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.core.session.NextTurnProvenanceDraft;

public record AskActionContext(
    NextTurnProvenanceDraft provenance,
    ToolResultEvent toolResult
) {}
