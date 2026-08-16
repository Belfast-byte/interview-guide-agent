package interview.guide.modules.interview.agent.adaptive.codeanalysis;

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

  public CodeTraceResult trace(String sessionId, String query) {
    String repositoryRef = analysisPersistenceService.getTraceRepositoryRef(sessionId);
    tracePersistenceService.reserve(sessionId, query);
    return new CodeTraceResult(
        query,
        traceSource.trace(repositoryRef, query, RESULT_LIMIT)
    );
  }
}
