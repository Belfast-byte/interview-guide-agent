package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.persistence.AdaptiveAgentSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
    traceCallRepository.save(new CodeTraceCallEntity(sessionId, sha256(query)));
  }

  private String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
          value.getBytes(StandardCharsets.UTF_8)
      ));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
