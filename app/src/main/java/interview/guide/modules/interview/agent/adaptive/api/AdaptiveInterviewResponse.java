package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveSessionStatus;
import java.util.List;

public record AdaptiveInterviewResponse(
    String sessionId,
    String runtimeVersion,
    AdaptiveSessionStatus status,
    int currentTurn,
    int maxTurns,
    String currentQuestion,
    List<AdaptiveInterviewTurnResponse> turns
) {

  public static AdaptiveInterviewResponse from(AdaptiveInterviewHistory history) {
    String currentQuestion = history.session().status() == AdaptiveSessionStatus.COMPLETED
        ? null
        : history.turns().getLast().question();
    return new AdaptiveInterviewResponse(
        history.session().id(),
        history.session().runtimeVersion(),
        history.session().status(),
        history.session().currentTurn(),
        history.session().maxTurns(),
        currentQuestion,
        history.turns().stream()
            .map(AdaptiveInterviewTurnResponse::from)
            .toList()
    );
  }
}
