package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.Arrays;
import java.util.List;

public record AssessmentContext(
    String dimension,
    String focus,
    String question,
    String answer,
    String toolResult,
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
        null,
        Arrays.stream(DepthLevel.values()).map(DepthRubricEntry::from).toList()
    );
  }

  public static AssessmentContext algorithmResult(
      String dimension,
      String focus,
      String question,
      String answer,
      String toolResult
  ) {
    return new AssessmentContext(
        dimension,
        focus,
        question,
        answer,
        toolResult,
        Arrays.stream(DepthLevel.values()).map(DepthRubricEntry::from).toList()
    );
  }
}
