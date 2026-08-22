package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import java.util.List;

/**
 * 自适应面试响应。
 */
public record AdaptiveInterviewResponse(
    String sessionId,
    String runtimeVersion,
    AdaptiveSessionStatus status,
    int currentTurn,
    int maxTurns,
    String currentQuestion,
    String failureReason,
    List<AdaptiveInterviewDimensionResponse> dimensions,
    List<AdaptiveInterviewTurnResponse> turns
) {

  public static AdaptiveInterviewResponse from(PlannedInterview interview) {
    var history = interview.history();
    String currentQuestion = history.session().status() == AdaptiveSessionStatus.COMPLETED
        || history.turns().isEmpty()
        ? null
        : history.turns().getLast().question();
    return new AdaptiveInterviewResponse(
        history.session().id(),
        history.session().runtimeVersion(),
        history.session().status(),
        history.session().currentTurn(),
        history.session().maxTurns(),
        currentQuestion,
        history.failureReason(),
        interview.plan().dimensions().stream()
            .map(AdaptiveInterviewDimensionResponse::from)
            .toList(),
        history.turns().stream()
            .map(AdaptiveInterviewTurnResponse::from)
            .toList()
    );
  }
}
