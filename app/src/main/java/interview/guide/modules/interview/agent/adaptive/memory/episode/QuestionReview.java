package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.QuestionRecallHint;
import java.util.List;

/** 草稿复核结果；REWRITE 时 hints 是下一次模型调用的唯一历史输入。 */
public record QuestionReview(
    QuestionNoveltyDecision.Type type,
    QuestionPublication publication,
    List<QuestionRecallHint> hints
) {

  public QuestionReview {
    hints = List.copyOf(hints);
  }
}
