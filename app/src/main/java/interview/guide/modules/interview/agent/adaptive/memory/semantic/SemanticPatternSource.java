package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagValue;

public record SemanticPatternSource(
    long episodeId,
    EpisodeTagValue value
) {}
