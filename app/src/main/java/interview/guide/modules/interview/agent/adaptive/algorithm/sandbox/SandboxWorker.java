package interview.guide.modules.interview.agent.adaptive.algorithm.sandbox;

/**
 * 沙箱 Worker 接口。
 */
public interface SandboxWorker {

  SandboxExecutionResult execute(
      SandboxExecution execution,
      SandboxExecutionSpec spec
  );
}
