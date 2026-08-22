package interview.guide.modules.interview.agent.adaptive.algorithm.judge;

import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecution;

/**
 * 算法结果就绪处理器接口。
 */
public interface AlgorithmResultReadyHandler {

  String SANDBOX_SUBMIT_TOOL_NAME = "sandbox_submit";

  void handle(SandboxExecution execution);
}
