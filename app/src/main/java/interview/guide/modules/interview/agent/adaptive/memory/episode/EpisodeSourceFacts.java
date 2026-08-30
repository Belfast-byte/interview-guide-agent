package interview.guide.modules.interview.agent.adaptive.memory.episode;

import java.util.Set;

/**
 * 一个 Episode 权威关系链允许引用的 source ID 集合。
 */
public record EpisodeSourceFacts(
    Set<Long> assessmentEvidenceIds,
    Set<Long> probeGapIds
) {

  public EpisodeSourceFacts {
    assessmentEvidenceIds = Set.copyOf(assessmentEvidenceIds);
    probeGapIds = Set.copyOf(probeGapIds);
  }

  public boolean contains(EpisodeTagSource source) {
    return switch (source.type()) {
      case ASSESSMENT_EVIDENCE -> assessmentEvidenceIds.contains(source.sourceId());
      case PROBE_GAP -> probeGapIds.contains(source.sourceId());
    };
  }
}
