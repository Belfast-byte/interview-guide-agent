package interview.guide.modules.interview.agent.adaptive.memory.profile;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;

/**
 * 原子增加 owner + TopicKey 对应的能力等级计数。
 */
public interface AbilityCounterIncrementStore {

  void increment(MemoryOwner owner, TopicKey topic, DepthLevel level);
}
