package interview.guide.modules.interview.agent.adaptive.memory.brief;

import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import java.util.List;

/**
 * 维度简报对应的轮次数据。
 */
public record DimensionBriefTurn(
    int turnIndex,
    String question,
    String answer
) {

  /**
   * 选取目标维度的轮次；当前回答覆盖对应轮次中尚未落库的 answer。
   */
  public static List<DimensionBriefTurn> forDimension(
      List<AdaptiveInterviewTurn> turns,
      PlannedDimension dimension,
      CandidateAnswer answer
  ) {
    return turns.stream()
        .filter(turn -> turn.dimensionOrder() == dimension.order())
        .map(turn -> new DimensionBriefTurn(
            turn.turnIndex(),
            turn.question(),
            turn.turnIndex() == answer.turnIndex() ? answer.content() : turn.answer()
        ))
        .toList();
  }
}
