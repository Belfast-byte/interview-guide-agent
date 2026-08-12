package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.persistence.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningAgent;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningRequest;
import interview.guide.modules.interview.agent.adaptive.runtime.BoundedReActRuntime;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActBudget;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdaptiveInterviewApplicationService {

  private final AdaptiveInterviewPersistenceService persistenceService;
  private final BoundedReActRuntime runtime;
  private final AdaptiveAgentProperties properties;
  private final AdaptiveAgentTelemetry telemetry;
  private final PlanningAgent planningAgent;

  public PlannedInterview create(String jd, String resume, String llmProvider) {
    String sessionId = UUID.randomUUID().toString();
    PlanProposal proposal = planningAgent.propose(
        new PlanningRequest(sessionId, jd, resume),
        llmProvider
    );
    InterviewPlan plan;
    try {
      plan = InterviewPlan.decide(sessionId, proposal);
    } catch (BusinessException e) {
      telemetry.planRejected(sessionId, e.getCode());
      throw e;
    }
    PlannedDimension firstDimension = plan.dimensionForTurn(1);
    RespondAction firstQuestion = runDecision(new ReActRequest(
        sessionId,
        llmProvider,
        jd,
        resume,
        plan.maxTurns(),
        firstDimension.dimension(),
        firstDimension.focus(),
        List.of(),
        null
    ));
    return persistenceService.create(
        sessionId,
        jd,
        resume,
        llmProvider,
        plan,
        firstQuestion
    );
  }

  public PlannedInterview submitAnswer(
      String sessionId,
      CandidateAnswer answer
  ) {
    PlannedInterview interview = persistenceService.get(sessionId);
    AdaptiveInterviewHistory history = interview.history();
    history.session().assertCanAnswer(answer);
    RespondAction action;
    if (interview.plan().isLastTurn(answer.turnIndex())) {
      action = RespondAction.finish("面试已覆盖全部规划维度。", "规划轮次已全部完成");
    } else {
      PlannedDimension nextDimension = interview.plan()
          .dimensionForTurn(answer.turnIndex() + 1);
      action = runDecision(new ReActRequest(
          sessionId,
          history.llmProvider(),
          history.jd(),
          history.resume(),
          history.session().maxTurns(),
          nextDimension.dimension(),
          nextDimension.focus(),
          history.turns(),
          answer
      ));
    }
    try {
      return persistenceService.recordDecision(sessionId, answer, action);
    } catch (OptimisticLockingFailureException e) {
      telemetry.stateConflict(sessionId, answer.turnIndex());
      throw new BusinessException(ErrorCode.BAD_REQUEST, "面试会话已被其他请求推进，请刷新后重试", e);
    }
  }

  public PlannedInterview get(String sessionId) {
    return persistenceService.get(sessionId);
  }

  private RespondAction runDecision(ReActRequest request) {
    long startedNanos = System.nanoTime();
    int inputTurn = request.candidateAnswer() == null
        ? 0
        : request.candidateAnswer().turnIndex();
    try {
      RespondAction action = runtime.run(request, budget());
      telemetry.decisionSucceeded(action.type(), startedNanos);
      return action;
    } catch (BusinessException e) {
      telemetry.decisionFailed(
          request.sessionId(),
          inputTurn,
          e.getCode(),
          startedNanos
      );
      throw e;
    }
  }

  private ReActBudget budget() {
    return new ReActBudget(
        properties.getMaxSteps(),
        properties.getMaxToolCalls(),
        properties.getDeadline()
    );
  }
}
