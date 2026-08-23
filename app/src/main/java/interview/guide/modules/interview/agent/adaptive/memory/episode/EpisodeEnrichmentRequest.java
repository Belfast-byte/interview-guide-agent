package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 从权威事实组装的 Episode enrichment 输入。
 */
public record EpisodeEnrichmentRequest(
    long episodeId,
    String sessionId,
    int turnIndex,
    TopicKey topic,
    String question,
    String answer,
    DepthLevel depthLevel,
    String assessmentSummary,
    List<EpisodeEvidenceFact> evidences,
    List<EpisodeProbeGapFact> probeGaps,
    List<EpisodeToolResultFact> toolResults
) {

  public EpisodeEnrichmentRequest {
    evidences = List.copyOf(evidences);
    probeGaps = List.copyOf(probeGaps);
    toolResults = List.copyOf(toolResults);
  }

  public EpisodeSourceFacts sourceFacts() {
    return new EpisodeSourceFacts(
        ids(evidences.stream().map(EpisodeEvidenceFact::id).toList()),
        ids(probeGaps.stream().map(EpisodeProbeGapFact::id).toList()),
        ids(toolResults.stream().map(EpisodeToolResultFact::id).toList())
    );
  }

  private Set<Long> ids(List<Long> values) {
    return values.stream().collect(Collectors.toUnmodifiableSet());
  }
}
