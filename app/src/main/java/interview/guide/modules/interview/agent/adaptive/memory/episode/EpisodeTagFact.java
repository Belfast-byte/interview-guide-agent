package interview.guide.modules.interview.agent.adaptive.memory.episode;

public record EpisodeTagFact(
    long id,
    long episodeId,
    EpisodeTagValue value,
    EpisodeTagSource source
) {}
