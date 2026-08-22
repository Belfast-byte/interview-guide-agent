package interview.guide.modules.interview.agent.adaptive.codeanalysis.trace;

import java.util.List;

/**
 * 代码轨迹来源接口。
 */
public interface CodeTraceSource {

  /**
   * @param tenantId 会话所属租户（服务端认定，不来自 repositoryRef）
   * @param sessionId 会话标识
   * @param repositoryRef 仓库快照 S3 key
   */
  List<CodeTraceMatch> trace(
      String tenantId,
      String sessionId,
      String repositoryRef,
      String query,
      int limit
  );
}
