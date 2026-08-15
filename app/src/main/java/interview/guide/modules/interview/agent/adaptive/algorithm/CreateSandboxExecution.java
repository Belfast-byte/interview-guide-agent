package interview.guide.modules.interview.agent.adaptive.algorithm;

public record CreateSandboxExecution(
    String sessionId,
    int turnIndex,
    String problemId,
    SandboxLanguage language,
    String codeRef,
    String codeHash,
    SandboxRunMode runMode
) {}
