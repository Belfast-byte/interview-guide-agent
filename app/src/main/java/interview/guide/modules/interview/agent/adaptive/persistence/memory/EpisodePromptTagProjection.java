package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagCategory;

/**
 * Prompt 投影所需的规范化 Episode 标签列。
 */
public interface EpisodePromptTagProjection {

  Long getEpisodeId();

  EpisodeTagCategory getCategory();

  String getTag();
}
