package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.time.LocalDateTime;

/**
 * 代码分析任务领域对象。
 */
public record CodeAnalysisJob(
    String id,
    String sessionId,
    String repositoryId,
    AnalysisJobStatus status,
    Long durationMs,
    Long tokenCost,
    LocalDateTime createdAt,
    LocalDateTime finishedAt
) {}
