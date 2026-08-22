package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.Set;

/**
 * 代码锚点目录接口。
 */
public interface CodeAnchorCatalog {

  /**
   * @param tenantId 会话所属租户（服务端认定，不来自 repositoryRef）
   * @param sessionId 会话标识
   * @param repositoryRef 仓库快照 S3 key
   */
  Set<CodeAnchor> findMissing(
      String tenantId,
      String sessionId,
      String repositoryRef,
      Set<CodeAnchor> anchors
  );
}
