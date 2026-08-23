package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentStatus;
import java.time.LocalDateTime;

/** 候选人记忆页可公开的 Episode 字段投影。 */
public interface CandidateMemoryEpisodeProjection {

  String getSessionId();

  int getTurnIndex();

  Integer getParentTurnIndex();

  String getSkillId();

  String getFocusId();

  DepthLevel getDepthLevel();

  EpisodeEnrichmentStatus getEnrichmentStatus();

  LocalDateTime getCreatedAt();
}
