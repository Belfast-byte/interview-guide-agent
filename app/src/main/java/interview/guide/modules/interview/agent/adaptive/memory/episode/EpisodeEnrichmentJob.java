package interview.guide.modules.interview.agent.adaptive.memory.episode;

/**
 * 从 Episode 与权威 Session 关系读取的 enrichment 投递参数。
 */
public record EpisodeEnrichmentJob(
    long episodeId,
    String llmProvider
) {}
