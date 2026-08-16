package interview.guide.modules.interview.agent.adaptive.codeanalysis.repo;

/**
 * 项目仓库快照。
 */
public record ProjectRepositorySnapshot(
    String tenantId,
    String sessionId,
    String repositoryRef,
    String commitHash
) {}
