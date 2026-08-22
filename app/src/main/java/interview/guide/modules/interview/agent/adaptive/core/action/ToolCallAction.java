package interview.guide.modules.interview.agent.adaptive.core.action;

import java.util.Map;

/**
 * Agent 工具调用动作，包含工具名和参数。
 */
public record ToolCallAction(
    String toolName,
    Map<String, Object> arguments,
    String reason
) implements AgentAction {}
