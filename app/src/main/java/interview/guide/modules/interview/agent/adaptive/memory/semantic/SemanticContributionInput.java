package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFact;

public record SemanticContributionInput(
    EpisodeFact episode,
    DepthLevel observedDepth,
    DepthLevel targetDepth
) {}
