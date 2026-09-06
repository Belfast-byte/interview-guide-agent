package interview.guide.modules.interview.agent.adaptive.memory.episode;

import java.util.List;

/**
 * LLM 返回的未信任 Episode enrichment 提案。
 */
public record EpisodeEnrichmentProposal(
    String answerSummary,
    List<EpisodeTagProposal> tags,
    interview.guide.modules.interview.agent.adaptive.memory.observation.MemoryObservationProposal observation
) {
  public EpisodeEnrichmentProposal(String answerSummary,List<EpisodeTagProposal> tags) {
    this(answerSummary,tags,null);
  }
}
