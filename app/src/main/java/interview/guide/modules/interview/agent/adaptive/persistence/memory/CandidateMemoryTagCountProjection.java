package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagCategory;

/** owner + TopicKey 下的归一化标签计数。 */
public interface CandidateMemoryTagCountProjection {

  String getSkillId();

  String getFocusId();

  EpisodeTagCategory getCategory();

  String getTag();

  long getTagCount();
}
