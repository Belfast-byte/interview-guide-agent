package interview.guide.modules.interview.agent.adaptive.codeanalysis.job;

/**
 * 代码分析 Worker 任务响应。
 */
public record CodeAnalysisWorkerJobResponse(
    String jobId,
    String repositoryRef,
    String commitHash
) {}
