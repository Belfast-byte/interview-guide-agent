package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.SessionTransition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdaptiveInterviewPersistenceService {

  private final AdaptiveAgentSessionRepository sessionRepository;
  private final AdaptiveAgentTurnRepository turnRepository;

  @Transactional
  public AdaptiveInterviewHistory create(
      String sessionId,
      String jd,
      String resume,
      String llmProvider,
      int maxTurns,
      String firstQuestion
  ) {
    AdaptiveInterviewSession session = AdaptiveInterviewSession
        .create(sessionId, maxTurns)
        .start();
    AdaptiveAgentSessionEntity sessionEntity = sessionRepository.save(
        new AdaptiveAgentSessionEntity(session, jd, resume, llmProvider)
    );
    turnRepository.save(new AdaptiveAgentTurnEntity(sessionId, 1, firstQuestion));
    return history(sessionEntity);
  }

  @Transactional
  public AdaptiveInterviewHistory recordDecision(
      String sessionId,
      CandidateAnswer answer,
      RespondAction proposedAction
  ) {
    AdaptiveAgentSessionEntity sessionEntity = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    SessionTransition transition = sessionEntity.toDomain().apply(answer, proposedAction);
    AdaptiveAgentTurnEntity turnEntity = turnRepository
        .findBySessionIdAndTurnIndex(sessionId, answer.turnIndex())
        .orElseThrow();

    turnEntity.complete(answer, transition.appliedAction());
    sessionEntity.apply(transition.session());
    sessionRepository.flush();

    if (transition.appliedAction().type() == AgentResponseType.ASK) {
      turnRepository.save(new AdaptiveAgentTurnEntity(
          sessionId,
          transition.session().currentTurn(),
          transition.appliedAction().content()
      ));
    }
    return history(sessionEntity);
  }

  @Transactional(readOnly = true)
  public AdaptiveInterviewHistory get(String sessionId) {
    AdaptiveAgentSessionEntity sessionEntity = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    return history(sessionEntity);
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
