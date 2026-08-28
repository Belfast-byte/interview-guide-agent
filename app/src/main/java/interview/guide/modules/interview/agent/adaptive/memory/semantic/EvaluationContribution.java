package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;

public record EvaluationContribution(
    SemanticSource source,
    DepthLevel level
) implements SemanticContribution {

  @Override
  public SemanticTrack track() {
    return SemanticTrack.EVALUATED_CAPABILITY;
  }
}
