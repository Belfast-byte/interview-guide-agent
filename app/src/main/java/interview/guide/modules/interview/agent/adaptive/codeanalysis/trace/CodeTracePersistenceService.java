package interview.guide.modules.interview.agent.adaptive.codeanalysis.trace;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.util.Sha256;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 代码轨迹持久化服务。
 */
@Service
@RequiredArgsConstructor
public class CodeTracePersistenceService {

  private static final int MAX_CALLS_PER_SESSION = 3;

  private final AdaptiveAgentSessionRepository sessionRepository;
  private final CodeTraceCallRepository traceCallRepository;

  @Transactional
  public void reserve(String sessionId, String query) {
    sessionRepository.findLockedById(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    if (traceCallRepository.countBySessionId(sessionId) >= MAX_CALLS_PER_SESSION) {
      throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "本场面试代码追踪次数已达上限");
    }
    traceCallRepository.save(new CodeTraceCallEntity(sessionId, Sha256.hex(query)));
  }
}
