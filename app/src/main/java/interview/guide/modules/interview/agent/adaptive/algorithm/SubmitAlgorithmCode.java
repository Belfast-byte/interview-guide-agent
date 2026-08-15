package interview.guide.modules.interview.agent.adaptive.algorithm;

public record SubmitAlgorithmCode(
    String sessionId,
    int turnIndex,
    String problemId,
    SandboxLanguage language,
    String source,
    SandboxRunMode runMode
) {}
