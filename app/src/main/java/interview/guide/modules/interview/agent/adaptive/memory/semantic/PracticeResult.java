package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;

public record PracticeResult(
    PracticeOutcome outcome,
    EpisodeAssistanceLevel assistance,
    DepthLevel targetDepth
) {}
