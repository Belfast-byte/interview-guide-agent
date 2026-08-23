package interview.guide.modules.interview.agent.adaptive.memory.episode;

/**
 * Episode 同步事实提交后应唤醒 enrichment 的领域事件。
 */
public record EpisodeEnrichmentRequested(
    long episodeId,
    String llmProvider
) {}
