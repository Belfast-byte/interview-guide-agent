package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;

/** 不再携带 WorkState revision 的 Episode 创建事实。 */
public record AgentEpisodeFactCreation(
    Ownership ownership,
    Source source,
    Evaluation evaluation
) {

  public record Ownership(MemoryOwner owner, String sessionId, SessionMode mode) {}

  public record Source(long turnId, int turnIndex, TopicKey topic) {}

  public record Evaluation(
      String targetId,
      EpisodeAssistanceLevel assistance,
      EpisodeClosureStatus closure
  ) {}
}
