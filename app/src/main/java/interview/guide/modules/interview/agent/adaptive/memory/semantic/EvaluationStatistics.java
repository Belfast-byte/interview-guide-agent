package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import java.util.List;

public record EvaluationStatistics(List<Long> levelCounts) {

  private static final int PROFICIENT_WEIGHT = 3;
  private static final int COMPETENT_WEIGHT = 2;

  public EvaluationStatistics {
    levelCounts = List.copyOf(levelCounts);
    if (levelCounts.size() != DepthLevel.values().length) {
      throw new IllegalArgumentException("正式能力统计必须覆盖 L0-L4");
    }
  }

  public long count(DepthLevel level) {
    return levelCounts.get(level.ordinal());
  }

  public long total() {
    return levelCounts.stream().mapToLong(Long::longValue).sum();
  }

  public EvaluatedAbility ability() {
    long weighted = 0;
    for (DepthLevel level : DepthLevel.values()) {
      weighted += (long) level.ordinal() * count(level);
    }
    if (weighted >= PROFICIENT_WEIGHT * total()) {
      return EvaluatedAbility.PROFICIENT;
    }
    return weighted >= COMPETENT_WEIGHT * total()
        ? EvaluatedAbility.COMPETENT
        : EvaluatedAbility.WEAK;
  }
}
