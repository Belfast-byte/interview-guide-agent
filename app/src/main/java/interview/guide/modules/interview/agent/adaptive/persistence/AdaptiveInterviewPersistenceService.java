package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.SessionTransition;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecution;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdaptiveInterviewPersistenceService {

  private final AdaptiveAgentSessionRepository sessionRepository;
  private final AdaptiveAgentTurnRepository turnRepository;
  private final AdaptiveAgentPlanRepository planRepository;
  private final AdaptiveAgentToolCallRepository toolCallRepository;

  @Transactional
  public PlannedInterview create(
      String sessionId,
      String jd,
      String resume,
      String llmProvider,
      InterviewPlan plan,
      RespondAction firstAction,
      List<ToolExecution> toolExecutions
  ) {
    AdaptiveInterviewSession session = AdaptiveInterviewSession
        .create(sessionId, plan.maxTurns())
        .start();
    AdaptiveAgentSessionEntity sessionEntity = sessionRepository.save(
        new AdaptiveAgentSessionEntity(session, jd, resume, llmProvider)
    );
    planRepository.saveAll(plan.dimensions().stream()
        .map(dimension -> new AdaptiveAgentPlanEntity(sessionId, dimension))
        .toList());
    turnRepository.save(new AdaptiveAgentTurnEntity(
        sessionId,
        1,
        plan.dimensionForTurn(1).order(),
        firstAction
    ));
    saveToolExecutions(sessionId, toolExecutions);
    return plannedInterview(sessionEntity, plan);
  }

  @Transactional
  public PlannedInterview recordDecision(
      String sessionId,
      CandidateAnswer answer,
      RespondAction proposedAction,
      List<ToolExecution> toolExecutions
  ) {
    AdaptiveAgentSessionEntity sessionEntity = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    List<AdaptiveAgentPlanEntity> planEntities = planRepository
        .findBySessionIdOrderByDimensionOrder(sessionId);
    InterviewPlan plan = toPlan(sessionId, sessionEntity.toDomain().maxTurns(), planEntities);
    if (!plan.isLastTurn(answer.turnIndex())
        && proposedAction.type() == AgentResponseType.FINISH) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "全部规划维度覆盖前不能结束面试");
    }
    InterviewPlan updatedPlan = plan.answer(answer.turnIndex());
    SessionTransition transition = sessionEntity.toDomain().apply(answer, proposedAction);
    AdaptiveAgentTurnEntity turnEntity = turnRepository
        .findBySessionIdAndTurnIndex(sessionId, answer.turnIndex())
        .orElseThrow();

    turnEntity.complete(answer, transition.appliedAction());
    sessionEntity.apply(transition.session());
    for (int index = 0; index < planEntities.size(); index++) {
      planEntities.get(index).apply(updatedPlan.dimensions().get(index));
    }
    sessionRepository.flush();

    if (transition.appliedAction().type() == AgentResponseType.ASK) {
      turnRepository.save(new AdaptiveAgentTurnEntity(
          sessionId,
          transition.session().currentTurn(),
          updatedPlan.dimensionForTurn(transition.session().currentTurn()).order(),
          transition.appliedAction()
      ));
    }
    saveToolExecutions(sessionId, toolExecutions);
    return plannedInterview(sessionEntity, updatedPlan);
  }

  @Transactional(readOnly = true)
  public PlannedInterview get(String sessionId) {
    AdaptiveAgentSessionEntity sessionEntity = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    InterviewPlan plan = toPlan(
        sessionId,
        sessionEntity.toDomain().maxTurns(),
        planRepository.findBySessionIdOrderByDimensionOrder(sessionId)
    );
    return plannedInterview(sessionEntity, plan);
  }

  private PlannedInterview plannedInterview(
      AdaptiveAgentSessionEntity sessionEntity,
      InterviewPlan plan
  ) {
    return new PlannedInterview(history(sessionEntity), plan);
  }

  private void saveToolExecutions(
      String sessionId,
      List<ToolExecution> toolExecutions
  ) {
    toolCallRepository.saveAll(toolExecutions.stream()
        .map(execution -> new AdaptiveAgentToolCallEntity(sessionId, execution))
        .toList());
  }

  private InterviewPlan toPlan(
      String sessionId,
      int maxTurns,
      List<AdaptiveAgentPlanEntity> entities
  ) {
    return new InterviewPlan(
        sessionId,
        maxTurns,
        entities.stream().map(AdaptiveAgentPlanEntity::toDomain).toList()
    );
  }

  private AdaptiveInterviewHistory history(AdaptiveAgentSessionEntity sessionEntity) {
    AdaptiveInterviewSession session = sessionEntity.toDomain();
    return new AdaptiveInterviewHistory(
        session,
        sessionEntity.jd(),
        sessionEntity.resume(),
        sessionEntity.llmProvider(),
        turnRepository.findBySessionIdOrderByTurnIndex(session.id()).stream()
            .map(AdaptiveAgentTurnEntity::toDomain)
            .toList()
    );
  }
}
