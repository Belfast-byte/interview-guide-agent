package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.time.LocalDateTime;

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
