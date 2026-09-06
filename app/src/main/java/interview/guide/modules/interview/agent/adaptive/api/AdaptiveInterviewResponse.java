package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView.TargetCoverage;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.memory.TargetWorkStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
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
        interview.coverage().askedTurns(),
        history.session().maxTurns(),
        history.session().settings().mode(),
        history.session().settings().candidateLevel(),
        history.session().settings().practiceScope().topics(),
        currentQuestion,
        history.failureReason(),
        history.llmProviderNameSnapshot(),
        history.llmModelSnapshot(),
        interview.coverage().targets().stream()
            .map(target -> AdaptiveInterviewDimensionResponse.from(
                target, displayStatus(history, target), assessedTurns(history, target)))
            .toList(),
        history.turns().stream()
            .map(AdaptiveInterviewTurnResponse::from)
            .toList()
    );
  }

  private static TargetWorkStatus displayStatus(
      AdaptiveInterviewHistory history,
      TargetCoverage coverage
  ) {
    boolean current = history.session().status() == AdaptiveSessionStatus.IN_PROGRESS
        && !history.turns().isEmpty()
        && history.turns().getLast().dimensionOrder() != null
        && history.turns().getLast().dimensionOrder() == coverage.target().identity().order();
    if (current) {
      return TargetWorkStatus.ACTIVE;
    }
    if (coverage.askedTurns() == 0) return TargetWorkStatus.PENDING;
    boolean fullyAssessed = assessedTurns(history, coverage) == coverage.askedTurns();
    if (fullyAssessed && coverage.openGapIds().isEmpty()) return TargetWorkStatus.COMPLETED;
    return history.session().status() == AdaptiveSessionStatus.COMPLETED
        ? TargetWorkStatus.EXHAUSTED : TargetWorkStatus.ACTIVE;
  }

  private static int assessedTurns(AdaptiveInterviewHistory history, TargetCoverage coverage) {
    return (int) history.turns().stream()
        .filter(turn -> java.util.Objects.equals(turn.dimensionOrder(), coverage.target().identity().order()))
        .filter(turn -> turn.answerStatus() == interview.guide.modules.interview.agent.adaptive.core.session.AnswerProcessingStatus.COMPLETED)
        .count();
  }

  /**
   * 自适应面试维度响应。
   */
  public record AdaptiveInterviewDimensionResponse(
      int order,
      String dimension,
      String focus,
      int allocatedTurns,
      DepthLevel expectedDepth,
      DepthLevel depthCeiling,
      List<CapabilityTarget.EvidenceObjective> evidenceObjectives,
      int completedTurns,
      TargetWorkStatus status
  ) {

    static AdaptiveInterviewDimensionResponse from(
        TargetCoverage coverage,
        TargetWorkStatus displayStatus,
        int assessedTurns
    ) {
      CapabilityTarget target = coverage.target();
      return new AdaptiveInterviewDimensionResponse(
          target.identity().order(),
          target.identity().dimension(),
          target.identity().focus(),
          target.budget().turnBudget(),
          target.depth().expected(),
          target.depth().ceiling(),
          target.evidenceObjectives(),
          assessedTurns,
          displayStatus
      );
    }
  }

  /**
   * 自适应面试轮次响应。
   */
  public record AdaptiveInterviewTurnResponse(
      int turnIndex,
      Integer dimensionOrder,
      String question,
      String answer,
      interview.guide.modules.interview.agent.adaptive.core.session.AnswerProcessingStatus answerStatus,
      String answerError
  ) {

    static AdaptiveInterviewTurnResponse from(AdaptiveInterviewTurn turn) {
      return new AdaptiveInterviewTurnResponse(
          turn.turnIndex(),
          turn.dimensionOrder(),
          turn.question(),
          turn.answer(),
          turn.answerStatus(),
          turn.answerError()
      );
    }
  }
}
