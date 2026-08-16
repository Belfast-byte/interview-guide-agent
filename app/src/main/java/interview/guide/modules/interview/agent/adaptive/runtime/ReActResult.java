package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import java.util.List;

/**
 * ReAct 执行结果，包含最终回复动作和工具执行轨迹。
 */
public record ReActResult(
    RespondAction response,
    List<ToolExecution> toolExecutions
) {

  public ReActResult {
    toolExecutions = List.copyOf(toolExecutions);
  }

  public static ReActResult withoutTools(RespondAction response) {
    return new ReActResult(response, List.of());
  }
}
