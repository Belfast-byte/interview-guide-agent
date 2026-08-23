package interview.guide.modules.interview.agent.adaptive.memory.working;

import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 按稳定顺序裁决下一条追问缺口。
 */
public final class ProbeGapSelector {

  private static final Comparator<ProbeGapCandidate> STABLE_ORDER = Comparator
      .comparingInt(ProbeGapCandidate::gapOrder)
      .thenComparingLong(ProbeGapCandidate::id);

  private ProbeGapSelector() {}

  public static Optional<ProbeGapCandidate> select(
      TopicKey currentTopic,
      List<ProbeGapCandidate> candidates,
      Set<Long> usedProbeGapIds
  ) {
    return candidates.stream()
        .filter(candidate -> candidate.topic().equals(currentTopic))
        .filter(candidate -> !usedProbeGapIds.contains(candidate.id()))
        .min(STABLE_ORDER);
  }
}
