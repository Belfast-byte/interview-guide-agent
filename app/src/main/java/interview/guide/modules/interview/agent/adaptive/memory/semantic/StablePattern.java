package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagValue;

public record StablePattern(
    EpisodeTagValue value,
    long episodeCount
) {}
