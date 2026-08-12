package interview.guide.modules.interview.agent.adaptive.runtime;

import java.util.Map;

public record ToolObservation(
    String toolName,
    Map<String, Object> arguments,
    boolean accepted,
    String resultId,
    String output
) {}
