package interview.guide.modules.interview.agent.adaptive.memory.profile;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.UnverifiedClaim;
import java.util.List;

/** 未验证候选人声明的只读存储端口。 */
public interface UnverifiedClaimSource {

  List<UnverifiedClaim> findUnverifiedClaims(MemoryOwner owner);
}
