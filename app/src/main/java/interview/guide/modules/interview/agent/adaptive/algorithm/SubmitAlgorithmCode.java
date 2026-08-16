package interview.guide.modules.interview.agent.adaptive.algorithm;

/**
 * 提交算法代码的请求。
 */
public record SubmitAlgorithmCode(
    String sessionId,
    int turnIndex,
    String problemId,
    SandboxLanguage language,
    String source,
    SandboxRunMode runMode
) {}
