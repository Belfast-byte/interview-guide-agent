package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmSessionFacts;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveSessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class JpaAlgorithmSessionFacts implements AlgorithmSessionFacts {

  private final AdaptiveAgentSessionRepository sessionRepository;
  private final AdaptiveAgentTurnRepository turnRepository;

  @Override
  public long lockCurrentTurn(String sessionId, int turnIndex) {
    AdaptiveAgentSessionEntity session = sessionRepository
        .findLockedByIdAndTenantIdIsNull(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    if (session.status() != AdaptiveSessionStatus.IN_PROGRESS
        || session.toDomain().currentTurn() != turnIndex) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "只能为当前进行中的轮次提交代码");
    }
    return turnRepository.findBySessionIdAndTurnIndex(sessionId, turnIndex)
        .orElseThrow()
        .id();
  }
}
