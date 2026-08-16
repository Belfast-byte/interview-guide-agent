package interview.guide.modules.interview.agent.runtime;

import java.util.Map;

/**
 * Agent 单步决策结果类型：提问、结束或调用工具。
 */
public sealed interface AgentStep {

  record CallTool(String toolName, Map<String, Object> arguments) implements AgentStep {

    public CallTool {
      arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
  }

  record Ask(String question) implements AgentStep {}

  record Finish(String reason) implements AgentStep {}
}
