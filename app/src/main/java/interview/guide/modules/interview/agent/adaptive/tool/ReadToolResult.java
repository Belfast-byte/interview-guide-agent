package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation.AdoptableSource;
import java.util.List;
import java.util.Map;

/** 只读工具的同步终态；不允许 Pending。 */
public sealed interface ReadToolResult
    permits ReadToolResult.Success, ReadToolResult.Empty,
    ReadToolResult.Timeout, ReadToolResult.Error {

  record Success(
      Map<String, Object> data,
      List<AdoptableSource> adoptableSources
  ) implements ReadToolResult {

    public Success {
      data = Map.copyOf(data);
      adoptableSources = List.copyOf(adoptableSources);
    }
  }

  record Empty(String message) implements ReadToolResult {}

  record Timeout(String message) implements ReadToolResult {}

  record Error(String message) implements ReadToolResult {}
}
