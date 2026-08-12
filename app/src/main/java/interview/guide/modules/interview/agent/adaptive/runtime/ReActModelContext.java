package interview.guide.modules.interview.agent.adaptive.runtime;

import java.util.List;

public record ReActModelContext(
    ReActRequest request,
    List<ToolObservation> observations
) {

  public ReActModelContext {
    observations = List.copyOf(observations);
  }
}
