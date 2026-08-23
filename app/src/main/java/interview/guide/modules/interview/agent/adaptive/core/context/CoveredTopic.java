package interview.guide.modules.interview.agent.adaptive.core.context;

/**
 * 候选人已覆盖主题值对象，用于长期记忆避免重复出题。
 */
public record CoveredTopic(String skillId, String focusId) {

  public CoveredTopic {
    TopicKey key = new TopicKey(skillId, focusId);
    skillId = key.skillId();
    focusId = key.focusId();
  }

  public TopicKey topicKey() {
    return new TopicKey(skillId, focusId);
  }
}
