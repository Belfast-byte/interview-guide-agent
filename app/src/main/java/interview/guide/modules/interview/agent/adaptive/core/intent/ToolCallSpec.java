package interview.guide.modules.interview.agent.adaptive.core.intent;

import java.util.Map;

/** 模型只提议参数；持久化后才允许执行。 */
public record ToolCallSpec(
    String toolName,
    Map<String, Object> arguments,
    String reason
) {

  public ToolCallSpec {
    arguments = Map.copyOf(arguments);
  }
}
