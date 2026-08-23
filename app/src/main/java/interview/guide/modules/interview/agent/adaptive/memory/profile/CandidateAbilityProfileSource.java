package interview.guide.modules.interview.agent.adaptive.memory.profile;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileSnapshot;
import java.util.List;

/** 候选人能力画像轨迹的只读端口。 */
public interface CandidateAbilityProfileSource {

  List<AbilityProfileSnapshot> trajectory(MemoryOwner owner);
}
