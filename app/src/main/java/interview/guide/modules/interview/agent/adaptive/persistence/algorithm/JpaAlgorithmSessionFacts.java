package interview.guide.modules.interview.agent.adaptive.persistence.algorithm;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmSessionFacts;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 基于 JPA 的算法会话事实实现，提供当前轮次锁定与状态查询。
 */
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

  @Override
  public int turnIndex(long turnId) {
    return turnRepository.findById(turnId).orElseThrow().turnIndex();
  }

  @Override
  public long turnId(String sessionId, int turnIndex) {
    return turnRepository.findBySessionIdAndTurnIndex(sessionId, turnIndex)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.NOT_FOUND,
            "面试轮次不存在"
        ))
        .id();
  }
}
