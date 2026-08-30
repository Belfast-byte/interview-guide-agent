package interview.guide.modules.interview.agent.adaptive.runtime;

import java.util.Map;

/** 模型提出的一次有序只读工具调用。 */
public record ReadToolCall(
    String toolName,
    Map<String, Object> arguments,
    String reason
) {

  public ReadToolCall {
    arguments = Map.copyOf(arguments);
  }
}
