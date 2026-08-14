package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.Arrays;
import java.util.List;

public record AssessmentContext(
    String dimension,
    String focus,
    String question,
    String answer,
    List<DepthRubricEntry> rubric
) {

  public AssessmentContext {
    rubric = List.copyOf(rubric);
  }

  public static AssessmentContext currentAnswer(
      String dimension,
      String focus,
      String question,
      String answer
  ) {
    return new AssessmentContext(
        dimension,
        focus,
        question,
        answer,
        Arrays.stream(DepthLevel.values()).map(DepthRubricEntry::from).toList()
    );
  }
}
