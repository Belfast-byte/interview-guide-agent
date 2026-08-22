package interview.guide.modules.interview.agent.adaptive.runtime;

import java.util.Map;

/**
 * 工具观察值对象，将工具执行结果作为反馈提供给模型。
 */
public record ToolObservation(
    String toolName,
    Map<String, Object> arguments,
    boolean accepted,
    String resultId,
    String output
) {}
