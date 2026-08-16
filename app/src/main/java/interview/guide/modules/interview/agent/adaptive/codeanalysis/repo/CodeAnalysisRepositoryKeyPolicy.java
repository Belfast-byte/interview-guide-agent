package interview.guide.modules.interview.agent.adaptive.codeanalysis.repo;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;

/**
 * 代码分析仓库快照 S3 key 命名空间策略。
 *
 * <p>repositoryRef 只接受平台生成的 S3 key，且必须落在
 * {@code code-analysis/{tenantId}/{sessionId}/} 前缀下。存储是单 bucket，与
 * 候选人源码、简历等对象混放；逃逸该前缀的 key 会构成跨租户对象读取通道，
 * 因此提交入口（code.submit_repo）与下载侧（S3ZipCodeTraceSource /
 * S3ZipCodeAnchorCatalog）都必须执行此归属校验。
 */
public final class CodeAnalysisRepositoryKeyPolicy {

  public static final String NAMESPACE_PREFIX = "code-analysis/";

  private CodeAnalysisRepositoryKeyPolicy() {}

  public static String prefixFor(String tenantId, String sessionId) {
    return NAMESPACE_PREFIX + tenantId + "/" + sessionId + "/";
  }

  public static boolean isOwned(String repositoryRef, String tenantId, String sessionId) {
    return repositoryRef.startsWith(prefixFor(tenantId, sessionId));
  }

  /**
   * 越界 key 按跨租户资源 404 语义拒绝，不泄露 key 是否存在。
   */
  public static void requireOwned(String repositoryRef, String tenantId, String sessionId) {
    if (!isOwned(repositoryRef, tenantId, sessionId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "代码仓库快照不存在");
    }
  }
}
