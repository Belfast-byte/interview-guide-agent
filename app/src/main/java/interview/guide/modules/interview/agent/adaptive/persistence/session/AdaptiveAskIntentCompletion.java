package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionPublication;

public record AdaptiveAskIntentCompletion(
    String sessionId,
    String intentId,
    QuestionPublication publication
) {

  public RespondAction action() {
    return publication.action();
  }
}
