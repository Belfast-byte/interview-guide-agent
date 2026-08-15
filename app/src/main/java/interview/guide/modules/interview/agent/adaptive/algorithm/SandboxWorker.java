package interview.guide.modules.interview.agent.adaptive.algorithm;

public interface SandboxWorker {

  SandboxExecutionResult execute(
      SandboxExecution execution,
      SandboxExecutionSpec spec
  );
}
