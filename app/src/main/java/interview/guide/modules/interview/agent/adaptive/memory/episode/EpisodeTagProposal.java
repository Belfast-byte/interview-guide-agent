package interview.guide.modules.interview.agent.adaptive.memory.episode;

/**
 * LLM 输出的未信任标签建议。
 */
public record EpisodeTagProposal(
    String category,
    String tag,
    String sourceType,
    Long sourceId
) {}
