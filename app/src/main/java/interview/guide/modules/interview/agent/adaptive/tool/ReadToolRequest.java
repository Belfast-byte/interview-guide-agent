package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import java.util.Map;

/** 服务端上下文与模型参数组成的请求级只读工具输入。 */
public record ReadToolRequest(
    AgentContext context,
    Map<String, Object> arguments,
    long deadlineNanos
) {

  public ReadToolRequest {
    arguments = Map.copyOf(arguments);
  }
}
