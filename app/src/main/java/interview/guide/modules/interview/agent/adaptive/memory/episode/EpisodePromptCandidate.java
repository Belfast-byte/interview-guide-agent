package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.EpisodePromptFact;

/**
 * 带稳定存储序号的 Prompt 候选项；序号只用于排序，不进入 Prompt。
 */
public record EpisodePromptCandidate(long sourceId, EpisodePromptFact fact) {}
