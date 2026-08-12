package interview.guide.modules.interview.agent.adaptive.tool;

import java.util.Map;
import org.springframework.ai.tool.ToolCallback;

public interface AdaptiveAgentTool {

  String name();

  ToolCallback callback();

  ToolResult execute(Map<String, Object> arguments);
}
