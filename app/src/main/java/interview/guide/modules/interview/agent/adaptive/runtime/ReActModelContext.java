package interview.guide.modules.interview.agent.adaptive.runtime;

import java.util.List;

/**
 * ReAct 模型上下文，包含原始请求和当前已累积的工具观察。
 */
public record ReActModelContext(
    ReActRequest request,
    List<ToolObservation> observations
) {

  public ReActModelContext {
    observations = List.copyOf(observations);
  }
}
