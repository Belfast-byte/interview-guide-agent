package interview.guide.modules.interview.agent.adaptive.memory.observation;

import java.util.List;
import interview.guide.modules.interview.agent.adaptive.core.context.*;

/** 业务调用者只依赖带 owner 边界的读端口。 */
public interface MemoryEvidenceSource {
  List<CapabilityBelief> beliefs(MemoryOwner owner, TopicKey topic);
  List<String> recentQuestions(MemoryOwner owner, TopicKey topic, int limit);
  static String reference(CapabilityBelief belief) {
    return "memory:capability:"+belief.capabilityKey()+"@"+belief.revision();
  }
}
