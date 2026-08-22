package interview.guide.modules.interview.agent.adaptive.codeanalysis.trace;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.util.Sha256;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 代码轨迹持久化服务：先校验配额，追踪成功后才落额度。
 */
@Service
@RequiredArgsConstructor
public class CodeTracePersistenceService {

  private static final int MAX_CALLS_PER_SESSION = 3;

  private final CodeTraceCallRepository traceCallRepository;

  /**
   * 校验本会话代码追踪额度，超限直接拒绝；不落库不加锁。
   */
  @Transactional(readOnly = true)
  public void verifyQuota(String sessionId) {
    if (traceCallRepository.countBySessionId(sessionId) >= MAX_CALLS_PER_SESSION) {
      throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "本场面试代码追踪次数已达上限");
    }
  }

  /**
   * 追踪成功后记录一次额度消耗。
   */
  @Transactional
  public void record(String sessionId, String query) {
    traceCallRepository.save(new CodeTraceCallEntity(sessionId, Sha256.hex(query)));
  }
}
