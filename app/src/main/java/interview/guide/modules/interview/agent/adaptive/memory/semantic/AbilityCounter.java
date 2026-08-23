package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import java.util.Optional;

/**
 * L0-L4 的不可变累计计数与确定性定级算法。
 */
public record AbilityCounter(
    long l0Count,
    long l1Count,
    long l2Count,
    long l3Count,
    long l4Count
) {

  private static final int PROFICIENT_MIN_AVERAGE = 3;
  private static final int COMPETENT_MIN_AVERAGE = 2;

  public AbilityCounter {
    if (l0Count < 0 || l1Count < 0 || l2Count < 0 || l3Count < 0 || l4Count < 0) {
      throw new IllegalArgumentException("能力计数不能为负数");
    }
  }

  public static AbilityCounter empty() {
    return new AbilityCounter(0, 0, 0, 0, 0);
  }

  public AbilityCounter increment(DepthLevel level) {
    return withCount(level, Math.incrementExact(count(level)));
  }

  public AbilityCounter decrement(DepthLevel level) {
    long current = count(level);
    if (current == 0) {
      throw new IllegalStateException(level + " 能力计数不能下溢");
    }
    return withCount(level, current - 1);
  }

  public long total() {
    return Math.addExact(
        Math.addExact(l0Count, l1Count),
        Math.addExact(Math.addExact(l2Count, l3Count), l4Count)
    );
  }

  public long weightedTotal() {
    long lower = Math.addExact(l1Count, Math.multiplyExact(2, l2Count));
    long upper = Math.addExact(
        Math.multiplyExact(3, l3Count),
        Math.multiplyExact(4, l4Count)
    );
    return Math.addExact(lower, upper);
  }

  public Optional<SemanticAbility> ability() {
    long total = total();
    if (total == 0) {
      return Optional.empty();
    }
    long weighted = weightedTotal();
    if (weighted >= Math.multiplyExact(PROFICIENT_MIN_AVERAGE, total)) {
      return Optional.of(SemanticAbility.PROFICIENT);
    }
    if (weighted >= Math.multiplyExact(COMPETENT_MIN_AVERAGE, total)) {
      return Optional.of(SemanticAbility.COMPETENT);
    }
    return Optional.of(SemanticAbility.WEAK);
  }

  public long count(DepthLevel level) {
    return switch (level) {
      case L0 -> l0Count;
      case L1 -> l1Count;
      case L2 -> l2Count;
      case L3 -> l3Count;
      case L4 -> l4Count;
    };
  }

  private AbilityCounter withCount(DepthLevel level, long count) {
    return switch (level) {
      case L0 -> new AbilityCounter(count, l1Count, l2Count, l3Count, l4Count);
      case L1 -> new AbilityCounter(l0Count, count, l2Count, l3Count, l4Count);
      case L2 -> new AbilityCounter(l0Count, l1Count, count, l3Count, l4Count);
      case L3 -> new AbilityCounter(l0Count, l1Count, l2Count, count, l4Count);
      case L4 -> new AbilityCounter(l0Count, l1Count, l2Count, l3Count, count);
    };
  }
}
