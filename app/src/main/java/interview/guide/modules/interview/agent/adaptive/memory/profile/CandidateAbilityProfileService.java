package interview.guide.modules.interview.agent.adaptive.memory.profile;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileSnapshot;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 候选人能力画像服务，维护跨会话的能力标签和轨迹。
 */
@Service
@RequiredArgsConstructor
public class CandidateAbilityProfileService {

  private final CandidateAbilityProfileSource source;

  public List<AbilityProfileSnapshot> trajectory(String candidateId) {
    return source.trajectory(new MemoryOwner(null, candidateId));
  }
}
