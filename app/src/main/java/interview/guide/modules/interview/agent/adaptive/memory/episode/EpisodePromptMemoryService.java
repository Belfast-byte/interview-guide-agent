package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.EpisodePromptFact;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 为当前面试主题装配受预算约束的历史 Episode 安全事实。
 */
@Service
@RequiredArgsConstructor
public class EpisodePromptMemoryService {

  private final EpisodePromptFactSource source;
  private final EpisodePromptSelector selector;

  public List<EpisodePromptFact> select(String currentSessionId, TopicKey currentTopic) {
    return selector.select(
        currentTopic,
        source.findCompletedHistory(currentSessionId, currentTopic.skillId())
    );
  }
}
