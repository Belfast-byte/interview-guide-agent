package interview.guide.modules.interview.agent.adaptive.runtime;

import java.util.List;
import java.util.Map;

/** Java 校验与只读工具返回模型的统一不可信数据边界信封。 */
public record DecisionObservation(
    String reference,
    Kind kind,
    String field,
    String message,
    String toolName,
    Map<String, Object> data,
    List<AdoptableSource> adoptableSources
) {

  public DecisionObservation {
    data = Map.copyOf(data);
    adoptableSources = List.copyOf(adoptableSources);
  }

  public enum Kind {
    BUDGET_EXHAUSTED,
    VALIDATION_REJECTION,
    TOOL_SUCCESS,
    TOOL_EMPTY,
    TOOL_TIMEOUT,
    TOOL_ERROR
  }

  public record AdoptableSource(
      String reference,
      String type,
      String id,
      String version
  ) {}
}
