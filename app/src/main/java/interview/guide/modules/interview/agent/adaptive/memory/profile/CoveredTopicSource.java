package interview.guide.modules.interview.agent.adaptive.memory.profile;

import interview.guide.modules.interview.agent.adaptive.core.context.CoveredTopic;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import java.util.List;

/** 已覆盖主题的只读存储端口。 */
public interface CoveredTopicSource {

  List<CoveredTopic> findCoveredTopics(MemoryOwner owner);
}
