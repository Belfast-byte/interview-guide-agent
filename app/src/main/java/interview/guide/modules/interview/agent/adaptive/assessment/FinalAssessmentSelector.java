package interview.guide.modules.interview.agent.adaptive.assessment;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * 最终评估选择器：同一维度的多次评估中取深度等级最高、轮次最新的一次作为最终结论。
 */
public final class FinalAssessmentSelector {

  private FinalAssessmentSelector() {}

  public static <T> Comparator<T> byDepthThenTurn(
      Function<T, DepthLevel> depthLevel,
      ToIntFunction<T> turnIndex
  ) {
    return Comparator.comparing(depthLevel).thenComparingInt(turnIndex);
  }
}
