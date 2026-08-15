package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;

public interface AdaptiveAgentTool {

  String name();

  ToolCallback callback();

  ToolResult execute(Map<String, Object> arguments);

  default ToolResult execute(ReActRequest request, Map<String, Object> arguments) {
    return execute(arguments);
  }
}
