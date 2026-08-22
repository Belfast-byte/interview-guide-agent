package interview.guide.modules.interview.agent.adaptive.runtime;

import java.time.Duration;

/**
 * ReAct 执行预算，包含最大步数、最大工具调用数和总截止时间。
 */
public record ReActBudget(
    int maxSteps,
    int maxToolCalls,
    Duration deadline
) {}
