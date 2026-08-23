package interview.guide.modules.interview.agent.adaptive.memory.episode;

/**
 * enrichment 可引用的当前轮工具结果。
 */
public record EpisodeToolResultFact(
    long id,
    String summary,
    String output
) {}
