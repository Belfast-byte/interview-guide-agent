package interview.guide.modules.interview.agent.adaptive.memory.semantic;

public record PracticeContribution(
    SemanticSource source,
    PracticeResult result
) implements SemanticContribution {

  @Override
  public SemanticTrack track() {
    return SemanticTrack.PRACTICE_MASTERY;
  }
}
