package interview.guide.modules.interview.agent.adaptive.codeanalysis;

public record CodeAnalysisWorkerJobResponse(
    String jobId,
    String repositoryRef,
    String commitHash
) {}
