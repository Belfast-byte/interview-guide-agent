package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import java.time.LocalDateTime;

/**
 * 数据库读取阶段的 Episode Prompt 白名单列。
 */
public interface EpisodePromptFactProjection {

  Long getEpisodeId();

  String getSkillId();

  String getFocusId();

  DepthLevel getDepthLevel();

  LocalDateTime getCreatedAt();
}
