package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
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
    SessionMode mode,
    CandidateLevel candidateLevel,
    List<TopicKey> practiceScope,
    String currentQuestion,
    String failureReason,
    String llmProviderName,
    String llmModel,
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
        history.session().settings().mode(),
        history.session().settings().candidateLevel(),
        history.session().settings().practiceScope().topics(),
        currentQuestion,
        history.failureReason(),
        history.llmProviderNameSnapshot(),
        history.llmModelSnapshot(),
        interview.workState() == null ? List.of() : interview.workState().targets().stream()
            .map(AdaptiveInterviewDimensionResponse::from)
            .toList(),
        history.turns().stream()
            .map(AdaptiveInterviewTurnResponse::from)
            .toList()
    );
  }
}
