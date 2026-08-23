package interview.guide.modules.interview.agent.adaptive.memory.episode;

/**
 * enrichment 可引用的 Assessment probe gap。
 */
public record EpisodeProbeGapFact(
    long id,
    String anchor,
    String missingPoint
) {}
