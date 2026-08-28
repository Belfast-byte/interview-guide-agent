package interview.guide.modules.interview.agent.adaptive.assessment.depth;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import java.util.Arrays;
import java.util.List;

/**
 * 自适应评估上下文，包含维度、考察重点、问题、回答、工具结果与深度量规。
 */
public record AssessmentContext(
    String dimension,
    String focus,
    String question,
    String answer,
    List<String> rubric
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
        Arrays.stream(DepthLevel.values()).map(DepthLevel::rubricLine).toList()
    );
  }
}
