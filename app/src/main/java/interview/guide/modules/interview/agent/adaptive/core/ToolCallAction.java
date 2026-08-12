package interview.guide.modules.interview.agent.adaptive.core;

import java.util.Map;

public record ToolCallAction(
    String toolName,
    Map<String, Object> arguments,
    String reason
) implements AgentAction {}
