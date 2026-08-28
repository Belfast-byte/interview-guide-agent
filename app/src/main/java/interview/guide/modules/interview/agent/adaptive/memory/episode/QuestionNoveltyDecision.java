package interview.guide.modules.interview.agent.adaptive.memory.episode;

import java.util.List;

/** 题目草稿的确定性重复裁决。 */
public record QuestionNoveltyDecision(
    Type type,
    Long sourceExposureId,
    Long sourceEpisodeId,
    List<EvaluationRecallView> recalls
) {

  public QuestionNoveltyDecision {
    recalls = List.copyOf(recalls);
  }

  public enum Type {
    ACCEPT,
    REWRITE
  }
}
