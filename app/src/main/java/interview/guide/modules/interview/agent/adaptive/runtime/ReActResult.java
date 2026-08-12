package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import java.util.List;

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
