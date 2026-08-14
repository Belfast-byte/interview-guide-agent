package interview.guide.modules.interview.agent.adaptive.assessment;

public record DepthRubricEntry(
    DepthLevel level,
    String meaning,
    String typicalPerformance,
    String actionTendency
) {

  static DepthRubricEntry from(DepthLevel level) {
    return new DepthRubricEntry(
        level,
        level.meaning(),
        level.typicalPerformance(),
        level.actionTendency()
    );
  }
}
