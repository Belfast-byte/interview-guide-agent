package interview.guide.modules.interview.agent.adaptive.memory.episode;

import java.util.List;

/**
 * LLM 返回的未信任 Episode enrichment 提案。
 */
public record EpisodeEnrichmentProposal(
    String answerSummary,
    List<EpisodeTagProposal> tags
) {}
