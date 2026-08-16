package interview.guide.modules.interview.agent.adaptive.algorithm;

/**
 * 创建沙箱执行的请求。
 */
public record CreateSandboxExecution(
    String sessionId,
    int turnIndex,
    SandboxWorkloadType workloadType,
    String problemId,
    String scenarioId,
    String workspaceRef,
    String testsRef,
    SandboxLanguage language,
    String codeRef,
    String codeHash,
    SandboxRunMode runMode
) {

  public CreateSandboxExecution(
      String sessionId,
      int turnIndex,
      String problemId,
      SandboxLanguage language,
      String codeRef,
      String codeHash,
      SandboxRunMode runMode
  ) {
    this(
        sessionId,
        turnIndex,
        SandboxWorkloadType.ALGORITHM,
        problemId,
        null,
        null,
        null,
        language,
        codeRef,
        codeHash,
        runMode
    );
  }
}
