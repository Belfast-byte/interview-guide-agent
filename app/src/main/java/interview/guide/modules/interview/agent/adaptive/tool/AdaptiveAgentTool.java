package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;

/**
 * 自适应 Agent 工具接口，定义工具名称、描述和参数 schema。
 */
public interface AdaptiveAgentTool {

  String name();

  ToolCallback callback();

  ToolResult execute(Map<String, Object> arguments);

  default ToolResult execute(ReActRequest request, Map<String, Object> arguments) {
    return execute(arguments);
  }
}
