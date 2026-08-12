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
      int maxTurns,
      String firstQuestion
  ) {
    AdaptiveInterviewSession session = AdaptiveInterviewSession
        .create(sessionId, maxTurns)
        .start();
    sessionRepository.save(new AdaptiveAgentSessionEntity(session, jd, resume));
    turnRepository.save(new AdaptiveAgentTurnEntity(sessionId, 1, firstQuestion));
    return history(session);
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

    if (transition.appliedAction().type() == AgentResponseType.ASK) {
      turnRepository.save(new AdaptiveAgentTurnEntity(
          sessionId,
          transition.session().currentTurn(),
          transition.appliedAction().content()
      ));
    }
    return history(transition.session());
  }

  @Transactional(readOnly = true)
  public AdaptiveInterviewHistory get(String sessionId) {
    AdaptiveInterviewSession session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ))
        .toDomain();
    return history(session);
  }

  private AdaptiveInterviewHistory history(AdaptiveInterviewSession session) {
    return new AdaptiveInterviewHistory(
        session,
        turnRepository.findBySessionIdOrderByTurnIndex(session.id()).stream()
            .map(AdaptiveAgentTurnEntity::toDomain)
            .toList()
    );
  }
}
