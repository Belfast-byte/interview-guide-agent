package interview.guide.modules.interview.agent.adaptive.codeanalysis.trace;

import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 代码轨迹服务。
 */
@Service
@RequiredArgsConstructor
public class CodeTraceService {

  private static final int RESULT_LIMIT = 10;

  private final CodeAnalysisPersistenceService analysisPersistenceService;
  private final CodeTracePersistenceService tracePersistenceService;
  private final CodeTraceSource traceSource;

  public CodeTraceResult trace(String tenantId, String sessionId, String query) {
    String repositoryRef = analysisPersistenceService.getTraceRepositoryRef(sessionId);
    tracePersistenceService.verifyQuota(sessionId);
    CodeTraceResult result = new CodeTraceResult(
        query,
        traceSource.trace(tenantId, sessionId, repositoryRef, query, RESULT_LIMIT)
    );
    // 先用后扣：追踪成功才消耗额度，失败不占配额
    tracePersistenceService.record(sessionId, query);
    return result;
  }
}
