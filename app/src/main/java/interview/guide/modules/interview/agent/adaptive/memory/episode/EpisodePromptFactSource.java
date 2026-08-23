package interview.guide.modules.interview.agent.adaptive.memory.episode;

import java.util.List;

/**
 * 已完成历史 Episode 的安全投影读取端口。
 */
public interface EpisodePromptFactSource {

  List<EpisodePromptCandidate> findCompletedHistory(
      String currentSessionId,
      String skillId
  );
}
