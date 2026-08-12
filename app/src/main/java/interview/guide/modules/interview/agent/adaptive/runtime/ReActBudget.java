package interview.guide.modules.interview.agent.adaptive.runtime;

import java.time.Duration;

public record ReActBudget(
    int maxSteps,
    int maxToolCalls,
    Duration deadline
) {}
