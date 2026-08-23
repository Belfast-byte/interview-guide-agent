package interview.guide.modules.interview.agent.adaptive.memory.episode;

import java.util.Objects;

/**
 * 带分类的白名单标签值。
 */
public record EpisodeTagValue(EpisodeTagCategory category, String tag) {

  public EpisodeTagValue {
    Objects.requireNonNull(category, "category 不能为空");
    Objects.requireNonNull(tag, "tag 不能为空");
    validate(category, tag);
  }

  public static EpisodeTagValue error(ErrorPattern tag) {
    return new EpisodeTagValue(EpisodeTagCategory.ERROR_PATTERN, tag.name());
  }

  public static EpisodeTagValue habit(AnswerHabit tag) {
    return new EpisodeTagValue(EpisodeTagCategory.ANSWER_HABIT, tag.name());
  }

  private static void validate(EpisodeTagCategory category, String tag) {
    try {
      switch (category) {
        case ERROR_PATTERN -> ErrorPattern.valueOf(tag);
        case ANSWER_HABIT -> AnswerHabit.valueOf(tag);
      }
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Episode tag 不在白名单: " + tag, e);
    }
  }
}
