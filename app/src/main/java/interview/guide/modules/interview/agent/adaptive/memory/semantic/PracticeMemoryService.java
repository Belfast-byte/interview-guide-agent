package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 规划只读取可追溯判断；旧平均等级与自动稳定标签不再影响出题。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PracticeMemoryService {
  private final SemanticStateSource stateSource;
  public PracticePlanningMemory planning(MemoryOwner owner, PracticeScope scope) {
    return new PracticePlanningMemory(scope.topics().stream().map(topic -> {
      try {
        var beliefs=stateSource.beliefs(owner,topic).stream()
            .sorted(java.util.Comparator.comparing(
                interview.guide.modules.interview.agent.adaptive.memory.observation.CapabilityBelief::needsVerification).reversed())
            .limit(5).toList();
        return new PracticePlanningTopic(topic,new PracticePlanningStatus(null,null,null),List.of(),beliefs);
      } catch(RuntimeException error) {
        log.warn("Memory unavailable during planning: {}",error.getClass().getSimpleName());
        return new PracticePlanningTopic(topic,new PracticePlanningStatus(null,null,null),List.of(),List.of());
      }
    }).toList());
  }
}
